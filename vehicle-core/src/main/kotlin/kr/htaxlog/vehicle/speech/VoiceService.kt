package kr.htaxlog.vehicle.speech

import kotlinx.coroutines.flow.StateFlow
import kr.htaxlog.vehicle.core.DomainStore
import kr.htaxlog.vehicle.core.VoiceApi
import kr.htaxlog.vehicle.core.VoiceFuel
import kr.htaxlog.vehicle.core.VoicePurpose
import kr.htaxlog.vehicle.core.VoiceResult

/** 한 번 듣기의 결과. [SpeechGateway]가 SDK 콜백을 이 값으로 바꾼다. */
sealed interface SttOutcome {
    data class Text(val text: String) : SttOutcome

    data class Error(val reason: String) : SttOutcome

    data object Timeout : SttOutcome
}

/** 음성 인식. 실제 구현은 [SpeechGateway](Gleo AI STT), 테스트는 가짜. */
interface SpeechRecognizer {
    val available: StateFlow<Boolean>

    suspend fun listenOnce(timeoutMs: Long): SttOutcome
}

/**
 * [VoiceApi] 구현 (F-09, F-18). 듣기 → 원문 → 파서. 값을 못 뽑으면 NotUnderstood(원문)로 돌려
 * 화면이 탭 입력으로 넘어가게 한다. 장소 이름은 [DomainStore.places]에서 읽는다.
 */
class VoiceService(
    private val stt: SpeechRecognizer,
    private val store: DomainStore,
    private val listenTimeoutMs: Long = LISTEN_TIMEOUT_MS,
    private val log: (String) -> Unit = {},
) : VoiceApi {
    override val available: StateFlow<Boolean> get() = stt.available

    override suspend fun listenPurpose(): VoiceResult<VoicePurpose> = listen { text ->
        val places = runCatching {
            store.places()
        }.onFailure { log("장소 읽기 실패: ${it.message}") }.getOrDefault(emptyList())
        PurposeVoiceParser.parse(text, places)
    }

    override suspend fun listenFuel(): VoiceResult<VoiceFuel> = listen { text -> FuelVoiceParser.parse(text) }

    private suspend fun <T> listen(parse: suspend (String) -> T?): VoiceResult<T> {
        if (!stt.available.value) return VoiceResult.Failed("음성 인식을 쓸 수 없습니다")
        return when (
            val out = runCatching { stt.listenOnce(listenTimeoutMs) }.getOrElse {
                SttOutcome.Error(
                    it.message ?: "UNKNOWN",
                )
            }
        ) {
            is SttOutcome.Text -> {
                val text = out.text.trim()
                if (text.isEmpty()) {
                    VoiceResult.Failed("말씀을 듣지 못했습니다")
                } else {
                    parse(text)?.let { VoiceResult.Recognized(text, it) } ?: VoiceResult.NotUnderstood(text)
                }
            }
            is SttOutcome.Error -> {
                log("STT 오류 ${out.reason}")
                VoiceResult.Failed("음성 인식에 실패했습니다. 다시 말씀하시거나 탭으로 입력해 주세요")
            }
            SttOutcome.Timeout -> VoiceResult.Failed("말씀을 듣지 못했습니다")
        }
    }

    companion object {
        /** 기능명세 F-09 "STT(≤ 8초)". */
        const val LISTEN_TIMEOUT_MS = 8_000L
    }
}
