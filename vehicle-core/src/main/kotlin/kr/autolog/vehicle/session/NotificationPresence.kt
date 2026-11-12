package kr.autolog.vehicle.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.autolog.vehicle.core.AppPresenceApi

/**
 * AutoLog 화면이 보이는가 — [TripRecordingService]가 알림 채널을 고를 때 쓴다(보이면 헤즈업 없이, 프론트 스펙 D-37).
 * 프로세스에 하나(서비스·조립 지점이 같은 값을 본다). 처음엔 안 보임 — 화면이 `onStart`에서 알린다.
 */
object NotificationPresence : AppPresenceApi {
    private val _appVisible = MutableStateFlow(false)
    val appVisible: StateFlow<Boolean> = _appVisible.asStateFlow()

    override fun setAppVisible(visible: Boolean) {
        _appVisible.value = visible
    }
}
