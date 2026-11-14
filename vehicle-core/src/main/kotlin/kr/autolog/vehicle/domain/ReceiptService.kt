package kr.autolog.vehicle.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.ConfirmResult
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.ExpenseDraft
import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.core.ReceiptApi
import kr.autolog.vehicle.core.RelayClient
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.TripCandidate
import kr.autolog.vehicle.core.VoiceFuel
import java.time.Clock

/**
 * [ReceiptApi] 구현 (F-19, F-30).
 * - QR: 서버에서 1회용 토큰 URL을 받는다(앱 A의 RelayClient). 토큰이 1회용이라 캐시하지 않는다.
 *   QR이 유효한 동안 [pollIntervalMs]마다 inbox를 받아, 폰에서 올린 영수증이 몇 초 안에 차 화면에 뜨게 한다
 *   (기술명세 3.2 "QR 화면 열린 동안 주기 동기화", F-30 "QR 스캔부터 차 화면 카드까지 15초 내").
 *   새 편지가 들어오면(=영수증 도착) 또는 QR이 만료되면 멈춘다. QR을 다시 발급하면 이전 폴링은 취소한다.
 * - 후보: [ReceiptMatcher].
 * - 확정: 검산 → 비용 저장(같은 사진도 다시 저장 가능) → inbox ack. 고친 필드는 editedFields로 남긴다.
 */
class ReceiptService(
    private val store: DomainStore,
    private val relay: RelayClient,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = {},
    /** QR 폴링을 돌릴 범위. null이면 폴링하지 않는다(테스트·폴링 불필요한 곳). */
    private val scope: CoroutineScope? = null,
    private val pollIntervalMs: Long = QR_POLL_MS,
) : ReceiptApi {
    @Volatile private var polling: Job? = null

    /** 이 프로세스에서 확정한 영수증 편지(receiptId). 같은 편지를 두 번 저장하지 않게. */
    private val confirmedIds = mutableSetOf<String>()

    override suspend fun issueQr(): QrResult {
        val result =
            try {
                relay.issueQrToken()
            } catch (e: Exception) {
                log("QR 발급 실패: ${e.message}")
                QrResult.Failed("서버에 연결하지 못했습니다")
            }
        if (result is QrResult.Issued) pollUntil(result.expiresAt)
        return result
    }

    /**
     * QR 만료 시각까지 inbox를 주기적으로 받는다. 처음 받은 편지 수를 기준으로, 그보다 늘면 영수증이 도착한 것으로 보고 멈춘다.
     * (확정 전 영수증은 ack하지 않아 매번 다시 오므로 "있다"가 아니라 "늘었다"로 판단한다.)
     */
    private fun pollUntil(expiresAt: Long) {
        val s = scope ?: return
        polling?.cancel()
        polling =
            s.launch {
                var baseline: Int? = null
                while (isActive && clock.millis() < expiresAt) {
                    val received = (runCatching { relay.syncNow() }.getOrNull() as? SyncResult.Ok)?.received
                    if (received != null) {
                        val before = baseline
                        if (before == null) {
                            baseline = received
                        } else if (received > before) {
                            log("QR 폴링: 새 편지 도착 → 멈춤")
                            break
                        }
                    }
                    delay(pollIntervalMs)
                }
            }
    }

    override suspend fun candidates(receipt: ReceiptPayload): List<TripCandidate> {
        val at = ReceiptMatcher.receiptTime(receipt) ?: return emptyList()
        return try {
            // 오래 이어진 운행도 잡히도록 시작 시각은 넉넉히 읽는다.
            val trips = store.trips(at - ReceiptMatcher.WINDOW_MS - LONG_TRIP_MS, at + ReceiptMatcher.WINDOW_MS)
            ReceiptMatcher.candidates(at, trips, store.places().map { it.toPlace() })
        } catch (e: Exception) {
            log("영수증 후보 조회 실패 receipt=${receipt.receiptId}: ${e.message}")
            emptyList()
        }
    }

    override suspend fun confirm(receipt: ReceiptPayload, tripId: String?, edits: ReceiptFields): ConfirmResult {
        val amount = edits.amountWon ?: return ConfirmResult.Rejected("결제 금액을 입력해 주세요")
        if (amount <= 0) return ConfirmResult.Rejected("결제 금액이 올바르지 않습니다")
        if (ReceiptArithmetic.check(edits) == false) {
            return ConfirmResult.Rejected("금액이 수량 × 단가 − 할인과 맞지 않습니다")
        }
        return try {
            // 같은 사진(sha256)도 다시 보내면 다시 저장한다(2026-11-15 사용자 결정). 겹친 것은 검토의 '중복 영수증'이 알려 준다(F-23).
            // 막는 것은 같은 편지(receiptId)를 두 번 확정하는 것뿐이다 — 확정하면 ack돼 inbox에서 빠지므로 이 프로세스 안만 보면 된다.
            if (!synchronized(confirmedIds) { confirmedIds.add(receipt.receiptId) }) {
                return ConfirmResult.Rejected("이미 저장된 영수증입니다")
            }
            val draft =
                ExpenseDraft(
                    tripId = tripId,
                    occurredAt = parseOrNull(edits.datetime) ?: ReceiptMatcher.receiptTime(receipt) ?: clock.millis(),
                    amountWon = amount,
                    liters = edits.liters,
                    unitPrice = edits.unitPrice,
                    discountWon = edits.discountWon ?: 0,
                    fuelType = edits.fuelType,
                    merchant = edits.merchant,
                    cardLast4 = last4(edits.cardLast4),
                    receiptId = receipt.receiptId,
                    receiptSha256 = receipt.sha256,
                    thumbUrl = receipt.thumbUrl,
                    ocrConfidence = receipt.confidence,
                    editedFields = editedFields(receipt.fields, edits),
                )
            val id = runCatching { store.saveExpense(draft) }
                .onFailure { synchronized(confirmedIds) { confirmedIds.remove(receipt.receiptId) } }
                .getOrThrow()
            // ack가 실패해도 비용은 저장됐다. InboxSyncWorker가 다음에 다시 ack한다.
            runCatching { store.ackReceipt(receipt.receiptId) }.onFailure { log("영수증 ack 실패 ${receipt.receiptId}") }
            ConfirmResult.Saved(id)
        } catch (e: Exception) {
            log("영수증 저장 실패 receipt=${receipt.receiptId}: ${e.message}")
            ConfirmResult.Rejected("저장하지 못했습니다")
        }
    }

    override suspend fun saveFuel(tripId: String?, fuel: VoiceFuel, source: ExpenseSource, at: Long): ConfirmResult {
        if (source == ExpenseSource.OCR) return ConfirmResult.Rejected("영수증은 확인 화면에서 저장합니다")
        val amount = fuel.amountWon ?: return ConfirmResult.Rejected("결제 금액을 입력해 주세요")
        if (amount <= 0) return ConfirmResult.Rejected("결제 금액이 올바르지 않습니다")
        if (fuel.liters != null && fuel.liters <= 0) return ConfirmResult.Rejected("주유량이 올바르지 않습니다")
        val fields = ReceiptFields(null, null, null, fuel.liters, fuel.unitPrice, 0, amount, null)
        if (ReceiptArithmetic.check(fields) == false) return ConfirmResult.Rejected("금액이 수량 × 단가와 맞지 않습니다")
        return try {
            val id =
                store.saveExpense(
                    ExpenseDraft(
                        tripId = tripId,
                        occurredAt = at,
                        amountWon = amount,
                        liters = fuel.liters,
                        unitPrice = fuel.unitPrice,
                        discountWon = 0,
                        fuelType = null,
                        merchant = null,
                        cardLast4 = null,
                        source = source,
                    ),
                )
            ConfirmResult.Saved(id)
        } catch (e: Exception) {
            log("주유 기록 저장 실패: ${e.message}")
            ConfirmResult.Rejected("저장하지 못했습니다")
        }
    }

    private fun parseOrNull(iso: String?): Long? =
        iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    companion object {
        /** QR 화면 동안 inbox 확인 주기. */
        const val QR_POLL_MS = 3_000L
        const val LONG_TRIP_MS = 12 * 60 * 60 * 1000L

        /** 숫자만 남겨 끝 4자리. 4자리가 안 되면 null — 카드번호 전체가 저장되지 않게 한다. */
        fun last4(raw: String?): String? = raw?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }

        fun editedFields(ocr: ReceiptFields, edits: ReceiptFields): Set<String> = buildSet {
            if (ocr.merchant != edits.merchant) add("merchant")
            if (ocr.datetime != edits.datetime) add("datetime")
            if (ocr.fuelType != edits.fuelType) add("fuelType")
            if (ocr.liters != edits.liters) add("liters")
            if (ocr.unitPrice != edits.unitPrice) add("unitPrice")
            if (ocr.discountWon != edits.discountWon) add("discountWon")
            if (ocr.amountWon != edits.amountWon) add("amountWon")
            if (ocr.cardLast4 != edits.cardLast4) add("cardLast4")
        }
    }
}
