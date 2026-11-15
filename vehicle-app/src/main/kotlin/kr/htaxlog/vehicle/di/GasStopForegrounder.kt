package kr.htaxlog.vehicle.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kr.htaxlog.vehicle.data.repository.TripSession
import kr.htaxlog.vehicle.data.usage.UsageTier
import kr.htaxlog.vehicle.nav.Navigator
import kr.htaxlog.vehicle.nav.Route

/**
 * 운행 중 등록된 주유소에 주차(P)하면 [주유 기록 추가](영수증 QR)를 바로 연다(F-19·F-30, 2026-11-15 사용자 결정).
 * 세션은 주유소 P에서 운행을 끝내지 않으므로(같은 운행) 운전자가 영수증을 찍어 보내고 다시 출발하면 된다.
 * 한 번 정차에 한 번만 연다 — 다시 움직이면(주차가 아니게 되면) 다음 정차에서 또 열 수 있다.
 * 다른 앱을 보고 있으면 [bringToFront]로 앱을 앞으로 가져온다(실차 flavor만, 결과 화면과 같은 방법).
 */
internal class GasStopForegrounder(
    session: Flow<TripSession>,
    tier: StateFlow<UsageTier>,
    private val navigator: Navigator,
    scope: CoroutineScope,
    private val atGasStation: suspend () -> Boolean,
    private val bringToFront: (() -> Boolean)? = null,
) {
    private val appVisible = MutableStateFlow(true)
    private var openedThisStop = false

    init {
        scope.launch {
            combine(session, tier, appVisible) { state, currentTier, visible -> Triple(state, currentTier, visible) }
                .collect { (state, currentTier, visible) ->
                    if (currentTier != UsageTier.FULL) {
                        openedThisStop = false
                        return@collect
                    }
                    if (state !is TripSession.Recording || openedThisStop || !atGasStation()) return@collect
                    openedThisStop = true
                    // 주차(FULL)를 Navigator가 아직 모를 수 있다(앱이 뒤에 있음) — 딥링크로 넘기면 주차가 확인될 때 연다
                    if (navigator.current != Route.ReceiptQr) navigator.handleDeepLink(Route.ReceiptQr)
                    if (!visible) bringToFront?.invoke()
                }
        }
    }

    fun setAppVisible(visible: Boolean) {
        appVisible.value = visible
    }
}
