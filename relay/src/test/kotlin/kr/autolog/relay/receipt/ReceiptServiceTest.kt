package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.inbox.InboxRepository
import kr.autolog.relay.inbox.InboxRow
import kr.autolog.relay.inbox.InboxService
import kr.autolog.relay.support.TestClock
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.InboxPayload
import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.support.TransactionOperations
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

class ReceiptServiceTest {
    private val clock = TestClock()
    private val props = RelayProperties()
    private val vehicleId = UUID.randomUUID()
    private val qrRepo = OneTokenRepo(vehicleId)
    private val receipts = MemReceipts()
    private val storage = MemStorage()
    private val inboxRepo = MemInbox()
    private var ocr: ReceiptOcr =
        ReceiptOcr { _, _ ->
            OcrResult(ReceiptFields("SK", null, "휘발유", 40.0, 1700, 0, 68_000, "5678-1234"), mapOf("amountWon" to 0.97))
        }

    private fun service() = ReceiptService(
        QrTokenService(qrRepo, props, clock),
        receipts,
        storage,
        { i, m -> ocr.extract(i, m) },
        InboxService(inboxRepo, props, clock, listOf(ReceiptThumbUrlRefresher(receipts, storage))),
        props,
        clock,
        TransactionOperations.withoutTransaction(),
    )

    private val image = "fake-image-bytes".toByteArray()

    /** 흰 바탕 800x400 JPEG. */
    private fun realJpeg(): ByteArray = ByteArrayOutputStream().also {
        val img = BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, 800, 400)
            dispose()
        }
        ImageIO.write(img, "jpg", it)
    }.toByteArray()

    @Test
    fun `업로드하면 OCR 결과가 마스킹·검산되어 차 inbox에 들어간다`() {
        val payload = service().handleUpload("car1", "tok", image, "image/jpeg")

        assertEquals(OcrStatus.OK, payload.ocrStatus)
        assertEquals("1234", payload.fields.cardLast4)
        assertEquals(true, payload.arithOk)
        val delivered = AutoLogJson.decodeFromString(InboxPayload.serializer(), inboxRepo.rows.single().payloadJson)
        assertEquals(payload, delivered)
        assertTrue(storage.objects.keys.any { it.endsWith("/original") })
    }

    @Test
    fun `OCR이 실패해도 썸네일만 담아 차로 보낸다`() {
        ocr = ReceiptOcr { _, _ -> error("Gemini 다운") }

        val payload = service().handleUpload("car1", "tok", image, "image/jpeg")

        assertEquals(OcrStatus.FAILED, payload.ocrStatus)
        assertNull(payload.fields.amountWon)
        assertTrue(payload.thumbUrl.startsWith("signed://"))
        assertEquals(1, inboxRepo.rows.size)
    }

    @Test
    fun `같은 사진도 새 QR로 보내면 또 들어간다`() {
        service().handleUpload("car1", "tok", image, "image/jpeg")
        qrRepo.issue("tok2")

        val again = service().handleUpload("car1", "tok2", image, "image/jpeg")

        assertEquals(2, receipts.rows.size)
        assertEquals(2, inboxRepo.rows.size)
        assertEquals(receipts.rows[0].sha256, again.sha256)
    }

    @Test
    fun `도중에 실패하면 QR을 쓰지 않아 같은 QR로 다시 보낼 수 있다`() {
        storage.failPut = true
        assertThrows<IllegalStateException> { service().handleUpload("car1", "tok", image, "image/jpeg") }
        assertEquals(false, qrRepo.used)

        storage.failPut = false
        val payload = service().handleUpload("car1", "tok", image, "image/jpeg")

        assertEquals(OcrStatus.OK, payload.ocrStatus)
        assertEquals(true, qrRepo.used)
    }

    @Test
    fun `응답을 못 받아 쓴 QR로 같은 사진을 다시 보내면 만료 대신 409`() {
        service().handleUpload("car1", "tok", image, "image/jpeg")

        val e = assertThrows<ApiException> { service().handleUpload("car1", "tok", image, "image/jpeg") }

        assertEquals("RECEIPT_DUPLICATE", e.code)
        assertEquals(1, inboxRepo.rows.size)
    }

    @Test
    fun `쓴 QR로 다른 사진을 보내도 409 — QR 하나로는 한 장만`() {
        service().handleUpload("car1", "tok", image, "image/jpeg")

        val e = assertThrows<ApiException> { service().handleUpload("car1", "tok", realJpeg(), "image/jpeg") }

        assertEquals("RECEIPT_DUPLICATE", e.code)
        assertEquals(1, receipts.rows.size)
    }

    @Test
    fun `같은 QR로 동시에 올라와 유니크 인덱스에 걸리면 409이고 파일을 지운다`() {
        receipts.failInsertWith = DuplicateKeyException("uq receipts(qr_token)")

        val e = assertThrows<ApiException> { service().handleUpload("car1", "tok", realJpeg(), "image/jpeg") }

        assertEquals("RECEIPT_DUPLICATE", e.code)
        assertTrue(storage.objects.isEmpty(), "남은 파일: ${storage.objects.keys}")
        assertTrue(inboxRepo.rows.isEmpty())
    }

    @Test
    fun `DB 기록이 실패하면 저장한 원본과 썸네일을 지운다`() {
        receipts.failInsertWith = IllegalStateException("DB 다운")

        assertThrows<IllegalStateException> { service().handleUpload("car1", "tok", realJpeg(), "image/jpeg") }

        assertTrue(storage.objects.isEmpty(), "남은 파일: ${storage.objects.keys}")
    }

    @Test
    fun `차가 inbox를 가져갈 때마다 썸네일 URL을 새로 서명한다`() {
        service().handleUpload("car1", "tok", realJpeg(), "image/jpeg")
        storage.signVersion = 2

        val item = InboxService(inboxRepo, props, clock, listOf(ReceiptThumbUrlRefresher(receipts, storage)))
            .pending(vehicleId)
            .single()

        assertTrue((item.payload as ReceiptPayload).thumbUrl.endsWith("?v=2"))
    }

    @Test
    fun `이미지가 아니면 토큰을 쓰기 전에 거부한다`() {
        assertThrows<ApiException> { service().handleUpload("car1", "tok", "x".toByteArray(), "application/pdf") }

        assertEquals(false, qrRepo.used)
    }

    @Test
    fun `QR 토큰이 틀리면 401`() {
        val e = assertThrows<ApiException> { service().handleUpload("car1", "wrong", image, "image/jpeg") }

        assertEquals("QR_TOKEN_INVALID", e.code)
    }
}

private class OneTokenRepo(private val vehicleId: UUID) : QrTokenRepository {
    var used = false
    private val issued = mutableSetOf(kr.autolog.relay.common.Tokens.sha256Hex("tok"))
    private var validHash = issued.single()

    /** 차가 QR을 새로 띄운 것처럼 새 토큰을 쓸 수 있게 한다. */
    fun issue(token: String) {
        validHash = kr.autolog.relay.common.Tokens.sha256Hex(token)
        issued += validHash
        used = false
    }

    override fun insert(tokenHash: String, vehicleId: UUID, expiresAt: Instant) = Unit

    override fun consume(vehicleCode: String, tokenHash: String, now: Instant): UUID? =
        if (!used && tokenHash == validHash) {
            used = true
            vehicleId
        } else {
            null
        }

    override fun isValid(vehicleCode: String, tokenHash: String, now: Instant) = !used && tokenHash == validHash

    override fun vehicleOf(vehicleCode: String, tokenHash: String): UUID? = vehicleId.takeIf { tokenHash in issued }
}

private class MemReceipts : ReceiptRepository {
    val rows = mutableListOf<ReceiptRecord>()
    var failInsertWith: Exception? = null

    override fun existsForToken(qrTokenHash: String) = rows.any { it.qrTokenHash == qrTokenHash }

    override fun insert(r: ReceiptRecord) {
        failInsertWith?.let { throw it }
        rows += r
    }

    override fun thumbKey(receiptId: UUID, vehicleId: UUID) =
        rows.firstOrNull { it.receiptId == receiptId && it.vehicleId == vehicleId }?.thumbKey

    override fun deleteExpired(now: Instant) = emptyList<String>()
}

private class MemStorage : ReceiptStorage {
    val objects = mutableMapOf<String, ByteArray>()
    var signVersion = 1
    var failPut = false

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        if (failPut) error("저장소 다운")
        objects[key] = bytes
    }

    override fun signedUrl(key: String) = "signed://$key?v=$signVersion"

    override fun delete(key: String) {
        objects.remove(key)
    }
}

private class MemInbox : InboxRepository {
    val rows = mutableListOf<InboxRow>()

    override fun insert(
        vehicleId: UUID,
        kind: String,
        payloadJson: String,
        createdAt: Instant,
        expiresAt: Instant,
    ): Long {
        rows += InboxRow(rows.size + 1L, vehicleId, kind, payloadJson, createdAt)
        return rows.size.toLong()
    }

    override fun deletePending(vehicleId: UUID, kind: String) = 0

    override fun findPending(vehicleId: UUID, now: Instant) = rows.toList()

    override fun delete(id: Long, vehicleId: UUID) = false

    override fun touchLastInbox(vehicleId: UUID, at: Instant) = Unit

    override fun deleteExpired(now: Instant) = 0
}
