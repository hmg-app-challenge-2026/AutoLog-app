package kr.autolog.vehicle.domain

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.ConfirmResult
import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.TripSummary
import kr.autolog.vehicle.core.VoiceFuel
import kr.autolog.vehicle.fake.FakeRelayClient
import kr.autolog.vehicle.fake.InMemoryDomainStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ReceiptServiceTest {
    private val store = InMemoryDomainStore()
    private val relay = FakeRelayClient()
    private val service = ReceiptService(store, relay)
    private val r = receipt()

    @Test
    fun `QR은 매번 서버에서 새로 받는다`() = runTest {
        service.issueQr()
        val qr = service.issueQr()

        assertTrue(qr is QrResult.Issued)
        assertEquals(2, relay.qrCalls)
    }

    @Test
    fun `서버 호출이 예외를 던져도 Failed로 돌려준다`() = runTest {
        val broken = ReceiptService(
            store,
            object : kr.autolog.vehicle.core.RelayClient {
                override suspend fun issueQrToken(): QrResult = error("timeout")

                override suspend fun syncNow() = error("timeout")
            },
        )

        assertTrue(broken.issueQr() is QrResult.Failed)
    }

    @Test
    fun `후보는 저장소의 운행과 주유소에서 찾는다`() = runTest {
        store.placeList += PlaceInfo("gs", "주유소", PlaceKind.GAS_STATION, 37.40, 127.10)
        store.tripList += TripSummary("t-1", "car-1", null, kst(9), kst(9, 30), endLat = 37.40, endLng = 127.10)

        val c = service.candidates(r)

        assertEquals("t-1", c.single().tripId)
        assertTrue(c.single().hasFuelStop)
    }

    @Test
    fun `확정하면 비용을 저장하고 ack한다`() = runTest {
        val edits = r.fields.copy(liters = 40.0, cardLast4 = "카드 1234-****-****-5678")

        val result = service.confirm(r, "t-1", edits)

        assertTrue(result is ConfirmResult.Saved)
        val e = store.expenses.single()
        assertEquals("t-1", e.tripId)
        assertEquals(68_000, e.amountWon)
        assertEquals("5678", e.cardLast4)
        assertEquals(setOf("cardLast4"), e.editedFields)
        assertEquals(kst(9, 20), e.occurredAt)
        assertEquals(listOf("r-1"), store.acked)
    }

    @Test
    fun `검산이 맞지 않으면 저장하지 않는다`() = runTest {
        val result = service.confirm(r, "t-1", r.fields.copy(amountWon = 80_000))

        assertTrue(result is ConfirmResult.Rejected)
        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `금액이 없으면 거부`() = runTest {
        assertTrue(service.confirm(r, null, r.fields.copy(amountWon = null)) is ConfirmResult.Rejected)
    }

    @Test
    fun `같은 영수증 편지는 두 번 저장하지 않는다`() = runTest {
        service.confirm(r, null, r.fields)

        val again = service.confirm(r, null, r.fields)

        assertTrue(again is ConfirmResult.Rejected)
        assertEquals(1, store.expenses.size)
    }

    @Test
    fun `같은 사진을 다시 보낸 영수증은 또 저장한다`() = runTest {
        service.confirm(r, null, r.fields)

        val again = service.confirm(r.copy(receiptId = "${r.receiptId}-2"), null, r.fields)

        assertTrue(again is ConfirmResult.Saved)
        assertEquals(2, store.expenses.size)
    }

    @Test
    fun `운행 없이도 확정할 수 있다`() = runTest {
        service.confirm(r, null, r.fields)

        assertNull(store.expenses.single().tripId)
    }

    @Test
    fun `음성으로 받은 주유 금액·리터를 운행에 연결해 저장한다`() = runTest {
        val r = service.saveFuel("t-1", VoiceFuel(68_000, 40.0), ExpenseSource.VOICE, kst(9, 20))

        assertTrue(r is ConfirmResult.Saved)
        val e = store.expenses.single()
        assertEquals(ExpenseSource.VOICE, e.source)
        assertEquals(40.0, e.liters!!, 0.0)
        assertNull(e.receiptSha256)
    }

    @Test
    fun `주유 금액이 없거나 단가 검산이 틀리면 저장하지 않는다`() = runTest {
        assertTrue(service.saveFuel(null, VoiceFuel(null, 40.0), ExpenseSource.TAP, 0) is ConfirmResult.Rejected)
        assertTrue(
            service.saveFuel(null, VoiceFuel(90_000, 40.0, 1_700), ExpenseSource.TAP, 0) is ConfirmResult.Rejected,
        )
        assertTrue(store.expenses.isEmpty())
    }

    private fun TestScope.clock(): Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
    }

    @Test
    fun `QR을 발급하면 만료까지 3초마다 inbox를 받는다`() = runTest {
        relay.qr = QrResult.Issued("https://x/r/a?t=1", expiresAt = 30_000)
        val polling = ReceiptService(store, relay, clock(), scope = backgroundScope)

        polling.issueQr()
        runCurrent()
        advanceTimeBy(15_001)

        assertEquals(6, relay.syncCalls) // 0·3·6·9·12·15초
        advanceTimeBy(60_000)
        assertEquals(10, relay.syncCalls) // 만료(30초) 뒤에는 멈춤
    }

    @Test
    fun `새 편지가 들어오면 영수증이 도착한 것으로 보고 폴링을 멈춘다`() = runTest {
        relay.qr = QrResult.Issued("https://x/r/a?t=1", expiresAt = 600_000)
        relay.sync = SyncResult.Ok(1) // 확정 안 한 영수증 1장이 이미 있음
        val polling = ReceiptService(store, relay, clock(), scope = backgroundScope)
        polling.issueQr()
        runCurrent()
        advanceTimeBy(6_000)

        relay.sync = SyncResult.Ok(2) // 폰에서 올린 영수증 도착
        advanceTimeBy(3_000)
        val calls = relay.syncCalls
        advanceTimeBy(30_000)

        assertEquals(calls, relay.syncCalls)
    }

    @Test
    fun `QR을 다시 발급하면 이전 폴링은 멈추고 새로 시작한다`() = runTest {
        relay.qr = QrResult.Issued("https://x/r/a?t=1", expiresAt = 600_000)
        val polling = ReceiptService(store, relay, clock(), scope = backgroundScope)
        polling.issueQr()
        runCurrent()
        polling.issueQr()
        runCurrent()

        advanceTimeBy(3_001)

        assertEquals(3, relay.syncCalls) // 첫 폴링 1번 + 새 폴링 0·3초 2번 (겹쳐 돌지 않음)
    }

    @Test
    fun `QR 발급이 실패하면 폴링하지 않는다`() = runTest {
        relay.qr = QrResult.Failed("서버 없음")
        val polling = ReceiptService(store, relay, clock(), scope = backgroundScope)

        polling.issueQr()
        advanceTimeBy(30_000)

        assertEquals(0, relay.syncCalls)
    }

    @Test
    fun `카드 끝자리는 숫자 4개가 아니면 버린다`() {
        assertEquals("5678", ReceiptService.last4("****5678"))
        assertNull(ReceiptService.last4("12"))
        assertNull(ReceiptService.last4(null))
    }
}
