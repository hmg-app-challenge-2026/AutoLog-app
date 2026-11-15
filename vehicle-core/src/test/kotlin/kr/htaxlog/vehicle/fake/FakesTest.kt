package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.htaxlog.shared.ReceiptFields
import kr.htaxlog.vehicle.core.ConfirmResult
import kr.htaxlog.vehicle.core.MailError
import kr.htaxlog.vehicle.core.MailResult
import kr.htaxlog.vehicle.core.ReportFile
import kr.htaxlog.vehicle.core.TripFacts
import kr.htaxlog.vehicle.core.VoiceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** 프론트가 Fake만으로 화면 흐름을 돌릴 수 있는지 확인한다. */
class FakesTest {
    private val trip = TripFacts("t-1", "car-1", 0L)

    @Test
    fun `가짜 맥락은 목적지와 도착을 바꿀 수 있다`() = runTest {
        val ctx = FakeContext()
        ctx.setDestination(null)

        assertEquals(null, ctx.destination.value)
        assertEquals(3, ctx.recentDestinations().size)
    }

    @Test
    fun `가짜 추천 엔진은 확신 추천과 운전자 목록을 준다`() = runTest {
        val engine = FakePurposeEngine()

        assertTrue(engine.suggest(trip).isConfident)
        assertEquals("u-1", engine.orderedUsers("car-1", 0L).first().first().userId)
    }

    @Test
    fun `가짜 음성은 듣고 나서 인식 결과를 준다`() = runTest {
        val r = FakeVoiceApi().listenFuel()

        assertEquals(70_000, (r as VoiceResult.Recognized).value.amountWon)
    }

    @Test
    fun `가짜 영수증은 금액이 없으면 확정을 거부한다`() = runTest {
        val api = FakeReceiptApi()
        val empty = ReceiptFields(null, null, null, null, null, null, null, null)

        val rejected = api.confirm(FakeReceiptApi.SAMPLE_FAILED, null, empty)
        val saved = api.confirm(FakeReceiptApi.SAMPLE_RECEIPT, "t-1", FakeReceiptApi.SAMPLE_RECEIPT.fields)

        assertTrue(rejected is ConfirmResult.Rejected)
        assertTrue(saved is ConfirmResult.Saved)
        assertEquals(1, api.confirmed.size)
    }

    @Test
    fun `가짜 메일은 설정이 없으면 NOT_CONFIGURED`() = runTest {
        val mail = FakeMailApi(configured = false)

        val r = mail.send(
            ReportFile(YearMonth.of(2026, 11), File("r.xlsx"), "12가3456", 42, 1234.5, 3),
            listOf("boss@example.com"),
        )

        assertEquals(MailError.NOT_CONFIGURED, (r as MailResult.Failed).error)
    }
}
