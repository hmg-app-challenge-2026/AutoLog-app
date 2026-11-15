package kr.htaxlog.vehicle.report

import kotlinx.coroutines.test.runTest
import kr.htaxlog.vehicle.core.MailError
import kr.htaxlog.vehicle.core.MailResult
import kr.htaxlog.vehicle.fake.FakeLocalStore
import kr.htaxlog.vehicle.fake.FakeMailApi
import kr.htaxlog.vehicle.fake.FakeReportApi
import kr.htaxlog.vehicle.fake.FakeSettingsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class MonthlySenderTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private class Memory(var month: YearMonth? = null) : MonthlySentStore {
        override fun lastSent() = month

        override fun save(month: YearMonth) {
            this.month = month
        }
    }

    @Test
    fun `매달 1~7일에 지난달을 한 번만 보낸다`() {
        val dec1 = at("2026-11-30T15:00:00Z") // 12/01 00:00 KST

        assertEquals(YearMonth.of(2026, 11), MonthlySender.monthToSend(dec1, seoul, null, true))
        assertEquals(YearMonth.of(2026, 11), MonthlySender.monthToSend(dec1, seoul, YearMonth.of(2026, 10), true))
        assertNull(MonthlySender.monthToSend(dec1, seoul, YearMonth.of(2026, 11), true))
        assertNull(MonthlySender.monthToSend(at("2026-12-07T15:00:00Z"), seoul, null, true)) // 12/08 KST
        assertNull(MonthlySender.monthToSend(dec1, seoul, null, enabled = false))
    }

    @Test
    fun `스위치가 켜져 있으면 지난달 운행기록부를 모든 수신자에게 보내고 기억한다`() = runTest {
        val settings = FakeSettingsApi().apply { setAutoMonthlySend(true) }
        val mail = FakeMailApi()
        val sent = Memory()
        val clock = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC)
        val sender = MonthlySender(settings, FakeReportApi(FakeLocalStore(clock)), mail, sent, clock)

        val r = sender.runOnce()

        assertEquals(
            MonthlySender.Outcome.Sent(YearMonth.of(2026, 11), listOf("accounting@example.com", "me@example.com")),
            r,
        )
        assertEquals(YearMonth.of(2026, 11), sent.month)
        assertEquals(1, mail.sent.size)
        assertEquals(MonthlySender.Outcome.NotDue, sender.runOnce())
    }

    @Test
    fun `메일 계정이 없거나 발송이 실패하면 기억하지 않고 다음에 다시 한다`() = runTest {
        val settings = FakeSettingsApi().apply { setAutoMonthlySend(true) }
        val clock = Clock.fixed(Instant.parse("2026-12-02T01:00:00Z"), ZoneOffset.UTC)
        val sent = Memory()

        val noAccount =
            MonthlySender(settings, FakeReportApi(FakeLocalStore(clock)), FakeMailApi(configured = false), sent, clock)
        val failing = MonthlySender(
            settings,
            FakeReportApi(FakeLocalStore(clock)),
            FakeMailApi(nextResult = MailResult.Failed(MailError.NETWORK, "offline")),
            sent,
            clock,
        )

        assertTrue(noAccount.runOnce() is MonthlySender.Outcome.Waiting)
        assertTrue(failing.runOnce() is MonthlySender.Outcome.Waiting)
        assertNull(sent.month)
    }
}
