package kr.htaxlog.vehicle.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kr.htaxlog.vehicle.core.PurposeDetail
import kr.htaxlog.vehicle.core.VoiceFuel
import kr.htaxlog.vehicle.core.VoiceResult
import kr.htaxlog.vehicle.fake.InMemoryDomainStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceServiceTest {
    private class FakeStt(var next: SttOutcome, ok: Boolean = true) : SpeechRecognizer {
        override val available: StateFlow<Boolean> = MutableStateFlow(ok)
        var calls = 0

        override suspend fun listenOnce(timeoutMs: Long): SttOutcome {
            calls++
            return next
        }
    }

    private val store = InMemoryDomainStore()

    @Test
    fun `알아들으면 원문과 목적을 돌려준다`() = runTest {
        val r = VoiceService(FakeStt(SttOutcome.Text(" 거래처 미팅 ")), store).listenPurpose()

        assertEquals(
            VoiceResult.Recognized(
                "거래처 미팅",
                kr.htaxlog.vehicle.core.VoicePurpose(PurposeDetail.CLIENT_VISIT, "거래처 미팅"),
            ),
            r,
        )
    }

    @Test
    fun `주유 금액·리터를 뽑는다`() = runTest {
        val r = VoiceService(FakeStt(SttOutcome.Text("6만8천원 40리터")), store).listenFuel()

        assertEquals(VoiceFuel(68_000, 40.0), (r as VoiceResult.Recognized).value)
    }

    @Test
    fun `값을 못 뽑으면 NotUnderstood로 원문만`() = runTest {
        val r = VoiceService(FakeStt(SttOutcome.Text("음")), store).listenFuel()

        assertEquals(VoiceResult.NotUnderstood("음"), r)
    }

    @Test
    fun `STT 오류·시간 초과는 Failed`() = runTest {
        assertTrue(
            VoiceService(FakeStt(SttOutcome.Error("NETWORK_ERROR")), store).listenPurpose() is VoiceResult.Failed,
        )
        assertTrue(VoiceService(FakeStt(SttOutcome.Timeout), store).listenPurpose() is VoiceResult.Failed)
    }

    @Test
    fun `STT를 못 쓰면 듣지 않고 Failed`() = runTest {
        val stt = FakeStt(SttOutcome.Text("출근"), ok = false)

        val r = VoiceService(stt, store).listenPurpose()

        assertTrue(r is VoiceResult.Failed)
        assertEquals(0, stt.calls)
    }
}
