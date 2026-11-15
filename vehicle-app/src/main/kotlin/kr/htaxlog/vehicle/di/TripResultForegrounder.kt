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

/** 주행 중 다른 앱을 보고 있다가 주차 상태에서 운행이 끝나면 결과 화면을 앞으로 가져온다. */
internal class TripResultForegrounder(
    session: Flow<TripSession>,
    tier: StateFlow<UsageTier>,
    private val navigator: Navigator,
    scope: CoroutineScope,
    private val canBringToFront: () -> Boolean,
    private val bringToFront: () -> Boolean,
) {
    private val appVisible = MutableStateFlow(true)
    private var lastBroughtTripId: String? = null

    init {
        scope.launch {
            combine(session, tier, appVisible) { state, currentTier, visible ->
                Triple(state, currentTier, visible)
            }.collect { (state, currentTier, visible) ->
                // [기록 이어가기] 뒤 같은 운행을 다시 주차하면 새 5초 대기를 보여 줘야 한다.
                if (state is TripSession.Recording && state.trip.tripId == lastBroughtTripId) {
                    lastBroughtTripId = null
                }
                val tripId = if (visible || currentTier != UsageTier.FULL) null else state.endingTripId()
                if (tripId == null || tripId == lastBroughtTripId || !canBringToFront()) return@collect
                navigator.showTripResult(Route.TripResult(tripId))
                if (bringToFront()) lastBroughtTripId = tripId
            }
        }
    }

    fun setAppVisible(visible: Boolean) {
        appVisible.value = visible
    }
}

/** 정차 대기는 아직 운행 중이다. 주차(FULL)일 때만 결과 화면을 전면에 띄운다. */
private fun TripSession.endingTripId(): String? = when (this) {
    is TripSession.AutoEnding -> trip.tripId
    is TripSession.PendingReview -> tripId
    else -> null
}
