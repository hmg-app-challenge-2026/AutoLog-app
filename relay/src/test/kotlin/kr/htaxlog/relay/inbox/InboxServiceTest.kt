package kr.htaxlog.relay.inbox

import kr.htaxlog.relay.common.ApiException
import kr.htaxlog.relay.common.RelayProperties
import kr.htaxlog.relay.support.TestClock
import kr.htaxlog.shared.InboxKind
import kr.htaxlog.shared.OcrStatus
import kr.htaxlog.shared.ReceiptFields
import kr.htaxlog.shared.ReceiptPayload
import kr.htaxlog.shared.UserDto
import kr.htaxlog.shared.UsersPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InboxServiceTest {
    private val clock = TestClock()
    private val repo = FakeInboxRepository()
    private val service = InboxService(repo, RelayProperties(), clock)
    private val carA = UUID.randomUUID()
    private val carB = UUID.randomUUID()

    private fun receipt(id: String) = ReceiptPayload(
        receiptId = id,
        sha256 = "h-$id",
        thumbUrl = "https://example/thumb",
        takenAt = null,
        fields = ReceiptFields("SK", null, "휘발유", 40.0, 1700, 0, 68000, "1234"),
        confidence = mapOf("amountWon" to 0.98),
        arithOk = true,
        ocrStatus = OcrStatus.OK,
    )

    @Test
    fun `영수증은 쌓이고 받은 그대로 돌려준다`() {
        service.deliver(carA, receipt("r1"))
        service.deliver(carA, receipt("r2"))

        val items = service.pending(carA)

        assertEquals(listOf(InboxKind.RECEIPT, InboxKind.RECEIPT), items.map { it.kind })
        assertEquals(receipt("r1"), items.first().payload)
        assertTrue(items.first().createdAt.endsWith("+09:00"))
    }

    @Test
    fun `직원 목록은 차가 가져가기 전에 두 번 바뀌면 최신 1건만 남는다`() {
        service.deliver(carA, UsersPayload(listOf(UserDto("u1", "김", null, null))))
        service.deliver(carA, UsersPayload(listOf(UserDto("u1", "김", null, null), UserDto("u2", "이", null, null))))

        val items = service.pending(carA)

        assertEquals(1, items.size)
        assertEquals(2, (items.single().payload as UsersPayload).users.size)
    }

    @Test
    fun `다른 차 편지는 보이지 않고 ack도 안 된다`() {
        val id = service.deliver(carA, receipt("r1"))

        assertTrue(service.pending(carB).isEmpty())
        assertEquals("INBOX_NOT_FOUND", assertThrows<ApiException> { service.ack(carB, id) }.code)
    }

    @Test
    fun `ack 하면 다시 조회해도 없다`() {
        val id = service.deliver(carA, receipt("r1"))

        service.ack(carA, id)

        assertTrue(service.pending(carA).isEmpty())
    }

    @Test
    fun `30일이 지나면 조회되지 않는다`() {
        service.deliver(carA, receipt("r1"))
        clock.advance(Duration.ofDays(30))

        assertTrue(service.pending(carA).isEmpty())
    }

    @Test
    fun `조회하면 마지막 inbox 조회 시각이 기록된다`() {
        service.pending(carA)

        assertEquals(clock.instant(), repo.lastInbox[carA])
    }
}

private class FakeInboxRepository : InboxRepository {
    private val rows = mutableListOf<Pair<InboxRow, Instant>>()
    val lastInbox = mutableMapOf<UUID, Instant>()
    private var seq = 0L

    override fun insert(
        vehicleId: UUID,
        kind: String,
        payloadJson: String,
        createdAt: Instant,
        expiresAt: Instant,
    ): Long {
        val row = InboxRow(++seq, vehicleId, kind, payloadJson, createdAt)
        rows += row to expiresAt
        return row.id
    }

    override fun deletePending(vehicleId: UUID, kind: String): Int {
        val before = rows.size
        rows.removeAll { (r, _) -> r.vehicleId == vehicleId && r.kind == kind }
        return before - rows.size
    }

    override fun findPending(vehicleId: UUID, now: Instant) =
        rows.filter { (r, exp) -> r.vehicleId == vehicleId && exp.isAfter(now) }.map { it.first }

    override fun delete(id: Long, vehicleId: UUID) = rows.removeAll { (r, _) -> r.id == id && r.vehicleId == vehicleId }

    override fun touchLastInbox(vehicleId: UUID, at: Instant) {
        lastInbox[vehicleId] = at
    }

    override fun deleteExpired(now: Instant): Int {
        val before = rows.size
        rows.removeAll { (_, exp) -> !exp.isAfter(now) }
        return before - rows.size
    }
}
