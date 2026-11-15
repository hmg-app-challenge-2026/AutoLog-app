package kr.htaxlog.vehicle.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kr.htaxlog.vehicle.core.sdkCall
import kotlin.coroutines.resume

/**
 * Gleo AI SpeechToText SDK(3.3.1) 래퍼 (F-09, F-18). 정차 화면에서만 쓴다.
 *
 * AAR 정적 분석 결과(sdk-findings.md):
 * - `SpeechToText(context, Mode)` → `initialize(OnInitializeListener)` → `addListener(ResultListener)` → `request()`
 * - 결과는 `ResultListener.onUpdated(text, isFinal)`, 실패는 `onError(ErrorType)`. 끝나면 `stop()`, 앱 종료 때 `release()`.
 *
 * [검증 필요] HYBRID 모드가 별도 `registerApp` 없이 동작하는지, isFinal이 한 번만 오는지, 마이크 권한을 SDK 서비스가 갖는지.
 */
class SpeechGateway(context: Context, mode: Mode = Mode.HYBRID) : SpeechRecognizer {
    private val stt = SpeechToText(context.applicationContext, mode)
    private val availableState = MutableStateFlow(false)
    private val mutex = Mutex()

    override val available: StateFlow<Boolean> = availableState.asStateFlow()

    /** 앱 시작 때 한 번. 실패하면 available=false 로 두고 화면은 마이크 버튼을 숨긴다. */
    suspend fun start() {
        val result =
            runCatching {
                sdkCall<Unit> { onComplete, onFailed ->
                    stt.initialize(
                        object : OnInitializeListener {
                            override fun onSuccess() = onComplete(Unit)

                            override fun onFailure(error: ErrorType) = onFailed(IllegalStateException(error.name))
                        },
                    )
                }
            }
        result.onFailure { Log.w(TAG, "STT 초기화 실패: ${it.message}") }
        availableState.value = result.isSuccess
    }

    fun release() {
        availableState.value = false
        runCatching { stt.release() }.onFailure { Log.w(TAG, "STT release 실패: ${it.message}") }
    }

    override suspend fun listenOnce(timeoutMs: Long): SttOutcome = mutex.withLock {
        if (!availableState.value) return SttOutcome.Error("NOT_INITIALIZED")
        var listener: ResultListener? = null
        try {
            withTimeoutOrNull<SttOutcome>(timeoutMs) {
                suspendCancellableCoroutine<SttOutcome> { cont ->
                    val l =
                        object : ResultListener {
                            override fun onUpdated(text: String, isFinal: Boolean) {
                                if (isFinal && cont.isActive) cont.resume(SttOutcome.Text(text))
                            }

                            override fun onError(error: ErrorType) {
                                if (cont.isActive) cont.resume(SttOutcome.Error(error.name))
                            }

                            override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit

                            override fun onStartedRecognition() = Unit

                            override fun onEndedRecognition() = Unit

                            override fun onReady() = Unit

                            override fun onUpdatedRms(rms: Double) = Unit
                        }
                    listener = l
                    stt.addListener(l)
                    stt.request()
                }
            } ?: SttOutcome.Timeout
        } finally {
            listener?.let { runCatching { stt.removeListener(it) } }
            runCatching { stt.stop() }
        }
    }

    private companion object {
        const val TAG = "HTaxLog/speech"
    }
}
