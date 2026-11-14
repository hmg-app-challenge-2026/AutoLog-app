package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.common.Tokens
import kr.autolog.relay.inbox.InboxService
import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 영수증 파이프라인 (F-41, 기술명세 4.5).
 * QR 재전송 확인 → QR 토큰 확인 → Gemini OCR → 마스킹·검산 → 원본·썸네일(EXIF 회전 반영) 저장
 * → (한 트랜잭션) QR 토큰 소모 + receipts 기록 + 차 inbox(RECEIPT).
 * 토큰은 끝까지 성공했을 때만 소모한다 — 중간에 실패하거나 폰 연결이 끊기면 같은 QR로 다시 보낼 수 있다.
 * 같은 사진을 새 QR로 여러 번 보내는 것은 막지 않는다(2026-11-15 사용자 결정). 막는 것은 QR 하나로 두 장이다 —
 * 응답만 못 받고 같은 QR로 다시 보내면 409(이미 보냄)로 알려 준다.
 * OCR이 실패해도 썸네일만 담아 차로 보낸다(차에서 수동 입력).
 */
@Service
class ReceiptService(
    private val qr: QrTokenService,
    private val repo: ReceiptRepository,
    private val storage: ReceiptStorage,
    private val ocr: ReceiptOcr,
    private val inbox: InboxService,
    private val props: RelayProperties,
    private val clock: Clock,
    private val tx: TransactionOperations,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleUpload(vehicleCode: String, token: String?, image: ByteArray, mimeType: String): ReceiptPayload {
        if (image.isEmpty() || !mimeType.startsWith("image/")) {
            throw ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_NOT_IMAGE", "이미지 파일만 올릴 수 있습니다")
        }
        val vehicleId = qr.vehicleOf(vehicleCode, token)
        val tokenHash = Tokens.sha256Hex(token.orEmpty())
        if (repo.existsForToken(tokenHash)) throw duplicate()
        qr.requireValid(vehicleCode, token)
        val sha = Tokens.sha256Hex(image)

        val now = clock.instant()
        val receiptId = UUID.randomUUID()

        val ocrResult =
            runCatching { ocr.extract(image, mimeType) }
                .onFailure { log.warn("영수증 OCR 실패 receipt={} : {}", receiptId, it.message) }
                .getOrNull()
        val fields = ocrResult?.fields?.let(ReceiptVerifier::mask)
        val arithOk = fields?.let(ReceiptVerifier::arithmeticOk)
        val status = if (ocrResult != null) OcrStatus.OK else OcrStatus.FAILED
        val thumb = Thumbnailer.jpeg(image)
        if (thumb == null) log.warn("영수증 이미지를 읽지 못함 receipt={} type={} → 원본을 썸네일로 사용", receiptId, mimeType)

        val originalKey = "$vehicleId/$receiptId/original"
        val thumbKey = if (thumb != null) "$vehicleId/$receiptId/thumb.jpg" else originalKey
        val stored = mutableListOf<String>()
        try {
            storage.put(originalKey, image, mimeType)
            stored += originalKey
            if (thumb != null) {
                storage.put(thumbKey, thumb, JPEG)
                stored += thumbKey
            }
            val payload =
                ReceiptPayload(
                    receiptId = receiptId.toString(),
                    sha256 = sha,
                    thumbUrl = storage.signedUrl(thumbKey),
                    takenAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atOffset(ZoneOffset.ofHours(9))),
                    fields = fields ?: EMPTY_FIELDS,
                    confidence = ocrResult?.confidence.orEmpty(),
                    arithOk = arithOk,
                    ocrStatus = status,
                )
            // 토큰 소모·receipts 행·inbox 편지는 함께 들어가거나 함께 빠진다.
            tx.executeWithoutResult {
                // 같은 QR로 두 장이 동시에 올라오면 하나만 소모에 성공한다
                qr.consume(vehicleCode, token)
                repo.insert(
                    ReceiptRecord(
                        receiptId = receiptId,
                        vehicleId = vehicleId,
                        sha256 = sha,
                        storageKey = originalKey,
                        thumbKey = thumbKey,
                        takenAt = now,
                        ocrStatus = status.name,
                        fields = fields,
                        confidence = ocrResult?.confidence.orEmpty(),
                        arithOk = arithOk,
                        expiresAt = now.plus(Duration.ofDays(props.receiptRetentionDays)),
                        qrTokenHash = tokenHash,
                    ),
                )
                inbox.deliver(vehicleId, payload)
            }
            return payload
        } catch (e: Exception) {
            // 저장해 둔 파일이 행 없이 남지 않게 지운다.
            stored.forEach { key -> runCatching { storage.delete(key) }.onFailure { log.warn("파일 정리 실패 {}", key) } }
            // 같은 QR로 동시에 두 번 올라오면 검사를 둘 다 통과하고 qr_token 유니크 인덱스에서 걸린다.
            if (e is DuplicateKeyException) throw duplicate()
            throw e
        }
    }

    /** 30일 지난 영수증 원본·썸네일과 행 삭제 (매일 04:30 KST). */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    fun purgeExpired() {
        val keys = repo.deleteExpired(clock.instant())
        keys.forEach { key -> runCatching { storage.delete(key) }.onFailure { log.warn("영수증 파일 삭제 실패 {}", key) } }
        if (keys.isNotEmpty()) log.info("영수증 만료 삭제 파일 {}개", keys.size)
    }

    private fun duplicate() = ApiException(HttpStatus.CONFLICT, "RECEIPT_DUPLICATE", "이 QR로 이미 보낸 영수증입니다")

    private companion object {
        const val JPEG = "image/jpeg"
        val EMPTY_FIELDS = ReceiptFields(null, null, null, null, null, null, null, null)
    }
}
