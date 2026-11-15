package kr.htaxlog.vehicle.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * NaviHelper SDK 게이트웨이 (F-06, F-07 폴백). NaviHelper SDK 3.0.5 기준.
 *
 * 호출 순서: [start] → (사용) → [stop]. SDK 콜백은 [NaviTracker]로 넘기고, 밖에는 Flow·suspend만 내보낸다.
 * SDK 시그니처는 AAR에서 확인했다. 에뮬레이터 동작은 아직 확인 전이다. [검증 필요]
 */
class NaviGateway(context: Context, private val tracker: NaviTracker = NaviTracker()) {
    private val navi = NaviHelper(context.applicationContext)

    val destination: StateFlow<DestinationSnapshot?> get() = tracker.destination
    val destinationHistory: StateFlow<List<DestinationChange>> get() = tracker.history
    val isRouting: StateFlow<Boolean> get() = tracker.isRouting
    val arrivals: SharedFlow<ArrivalEvent> get() = tracker.arrivals

    private val listener =
        object : NaviHelperEventListener {
            override fun onDestinationInfo(info: DestinationInfo) =
                tracker.onEvent(NaviEvent.DestinationInfoReceived(info.toSnapshot()))

            override fun onDestinationChanged() {
                tracker.onEvent(NaviEvent.DestinationChanged)
                navi.getDestinationInfo() // 바뀐 값은 onDestinationInfo로 온다
            }

            override fun onRouteStateInfo(info: RouteStateInfo) =
                tracker.onEvent(NaviEvent.RoutingStateReceived(info.isRouting))

            override fun onRouteStarted(info: RouteStartInfo) {
                navi.getDestinationInfo()
                tracker.onEvent(NaviEvent.RoutingStateReceived(true))
            }

            override fun onRouteCancelled() = tracker.onEvent(NaviEvent.RouteCleared)

            override fun onRouteEnded() = tracker.onEvent(NaviEvent.RouteCleared)

            override fun onDestinationArrived(info: DestinationArrivedInfo) =
                tracker.onEvent(NaviEvent.Arrived(info.name, info.address))

            override fun onCurrentLocationInfo(info: CurrentLocationInfo) = tracker.onEvent(
                NaviEvent.CurrentLocationReceived(NaviLocation(info.latitude, info.longitude, info.address)),
            )

            override fun onRecentDestinationInfo(list: List<RecentDestinationInfo>) =
                tracker.onEvent(NaviEvent.RecentDestinationsReceived(list.map { it.toRecent() }))

            override fun onError(code: NaviErrorCode) {
                Log.w(TAG, "NaviHelper error: $code")
                tracker.onEvent(NaviEvent.Error(code.name))
            }
        }

    /** NaviHelper 서비스에 연결하고 리스너를 등록한다. 실패하면 예외를 던진다. */
    suspend fun start() {
        suspendCancellableCoroutine { cont ->
            navi.initialize(
                { if (cont.isActive) cont.resume(Unit) },
                { e -> if (cont.isActive) cont.resumeWithException(e) },
            )
        }
        navi.addListener(listener)
        navi.getRouteStateInfo()
        navi.getDestinationInfo()
    }

    fun stop() {
        navi.removeListener(listener)
        navi.release()
    }

    /** 목적지를 다시 조회한다. 응답이 없으면 현재 알고 있는 값. */
    suspend fun refreshDestination(): DestinationSnapshot? =
        tracker.requestAndAwait<NaviEvent.DestinationInfoReceived> { navi.getDestinationInfo() }?.destination
            ?: tracker.destination.value

    /** 내비가 아는 현재 위치. 출발지 폴백용(F-07). 응답이 없으면 null. */
    suspend fun currentLocation(): NaviLocation? =
        tracker.requestAndAwait<NaviEvent.CurrentLocationReceived> { navi.getCurrentLocationInfo() }?.location

    /** 최근 목적지 목록. 목적지 미설정 운행의 후보(F-06). 응답이 없으면 빈 목록. */
    suspend fun recentDestinations(): List<RecentDestination> =
        tracker.requestAndAwait<NaviEvent.RecentDestinationsReceived> { navi.getRecentDestinationInfo() }
            ?.destinations
            .orEmpty()

    private fun DestinationInfo.toSnapshot() = DestinationSnapshot(
        label = name,
        address = address,
        lat = latitude,
        lng = longitude,
        poiId = poiId,
        remainDistanceM = remainDistance,
    )

    private fun RecentDestinationInfo.toRecent() = RecentDestination(name, address, latitude, longitude)

    private companion object {
        const val TAG = "HTaxLog/navi"
    }
}
