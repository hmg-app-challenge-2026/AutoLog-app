package kr.htaxlog.vehicle.fake

import kr.htaxlog.shared.OcrStatus
import kr.htaxlog.shared.ReceiptFields
import kr.htaxlog.shared.ReceiptPayload
import kr.htaxlog.vehicle.core.ConfirmResult
import kr.htaxlog.vehicle.core.ExpenseSource
import kr.htaxlog.vehicle.core.QrResult
import kr.htaxlog.vehicle.core.ReceiptApi
import kr.htaxlog.vehicle.core.TripCandidate
import kr.htaxlog.vehicle.core.VoiceFuel

/**
 * [ReceiptApi] 가짜. 확정한 내용은 [confirmed]에 쌓인다.
 * 영수증 확인 화면은 [SAMPLE_RECEIPT](모두 확신) / [SAMPLE_UNSURE](리터 낮음·검산 실패) / [SAMPLE_FAILED](OCR 실패)로 그린다.
 */
class FakeReceiptApi(
    var qr: QrResult = QrResult.Issued("https://htaxlog.example/r/abcd1234?t=demo", Long.MAX_VALUE),
    var tripCandidates: List<TripCandidate> = SAMPLE_CANDIDATES,
) : ReceiptApi {
    val confirmed = mutableListOf<Triple<ReceiptPayload, String?, ReceiptFields>>()

    override suspend fun issueQr(): QrResult = qr

    override suspend fun candidates(receipt: ReceiptPayload): List<TripCandidate> = tripCandidates.take(3)

    val fuelEntries = mutableListOf<Pair<String?, VoiceFuel>>()

    override suspend fun saveFuel(tripId: String?, fuel: VoiceFuel, source: ExpenseSource, at: Long): ConfirmResult {
        if (fuel.amountWon == null) return ConfirmResult.Rejected("결제 금액이 없습니다")
        fuelEntries += tripId to fuel
        return ConfirmResult.Saved("fuel-${fuelEntries.size}")
    }

    override suspend fun confirm(receipt: ReceiptPayload, tripId: String?, edits: ReceiptFields): ConfirmResult {
        if (edits.amountWon == null) return ConfirmResult.Rejected("결제 금액이 없습니다")
        confirmed += Triple(receipt, tripId, edits)
        return ConfirmResult.Saved("exp-${confirmed.size}")
    }

    companion object {
        /** 가짜 주소라 이미지가 열리지 않는다. 화면은 로드 실패 자리표시를 보여 준다. */
        private const val THUMB = "https://htaxlog.example/thumb/r-1.jpg"

        val SAMPLE_RECEIPT =
            ReceiptPayload(
                receiptId = "r-1",
                sha256 = "0".repeat(64),
                thumbUrl = THUMB,
                takenAt = "2026-11-02T09:40:00+09:00",
                fields = ReceiptFields("SK 판교주유소", "2026-11-02T09:20:00+09:00", "휘발유", 40.0, 1700, 0, 68_000, "1234"),
                confidence = mapOf("merchant" to 0.95, "liters" to 0.93, "unitPrice" to 0.92, "amountWon" to 0.98),
                arithOk = true,
                ocrStatus = OcrStatus.OK,
            )
        val SAMPLE_UNSURE =
            SAMPLE_RECEIPT.copy(
                receiptId = "r-2",
                fields = SAMPLE_RECEIPT.fields.copy(liters = 4.0),
                confidence = SAMPLE_RECEIPT.confidence + ("liters" to 0.41),
                arithOk = false,
            )
        val SAMPLE_FAILED =
            SAMPLE_RECEIPT.copy(
                receiptId = "r-3",
                fields = ReceiptFields(null, null, null, null, null, null, null, null),
                confidence = emptyMap(),
                arithOk = null,
                ocrStatus = OcrStatus.FAILED,
            )
        val SAMPLE_CANDIDATES =
            listOf(
                TripCandidate("t-1", 1_762_042_320_000, 1_762_044_000_000, "09:12 본사 → 판교 거래처", hasFuelStop = true),
                TripCandidate("t-2", 1_762_035_000_000, 1_762_037_400_000, "07:10 자택 → 본사", hasFuelStop = false),
            )
    }
}
