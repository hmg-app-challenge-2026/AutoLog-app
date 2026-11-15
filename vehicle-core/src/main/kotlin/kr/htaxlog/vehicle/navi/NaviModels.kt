package kr.htaxlog.vehicle.navi

/** 내비 목적지 한 건. SDK 타입을 밖으로 내보내지 않기 위한 HTaxLog 모델. (F-06) */
data class DestinationSnapshot(
    val label: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val poiId: String?,
    /** 남은 거리(m). 도착 판정 보조용. */
    val remainDistanceM: Int?,
)

/** 목적지가 바뀐 기록. 운행 중 변경 이력(F-06 "변경 시 최종값 + 이력")에 쓴다. */
data class DestinationChange(val destination: DestinationSnapshot?, val at: Long)

/** 내비가 알려준 도착. 앱 A의 정차 감지 신호(F-04 "RouteState=도착")로 쓴다. */
data class ArrivalEvent(val label: String?, val address: String?, val at: Long)

/** 내비 "현재 위치" 응답. Fused Location이 없을 때 출발지 폴백(F-07). */
data class NaviLocation(val lat: Double, val lng: Double, val address: String?)

/** 최근 목적지 한 건. 목적지 미설정 시 후보 목록(F-06). */
data class RecentDestination(val label: String, val address: String?, val lat: Double, val lng: Double)

/**
 * SDK 리스너 콜백을 HTaxLog 이벤트로 옮긴 것.
 * NaviGateway가 SDK 콜백을 이 이벤트로 바꿔 NaviTracker에 넣는다.
 */
sealed interface NaviEvent {
    data class DestinationInfoReceived(val destination: DestinationSnapshot) : NaviEvent

    /** 목적지가 바뀌었다는 알림만 온다. 실제 값은 getDestinationInfo() 응답으로 온다. */
    data object DestinationChanged : NaviEvent

    data object RouteCleared : NaviEvent

    data class RoutingStateReceived(val isRouting: Boolean) : NaviEvent

    data class Arrived(val label: String?, val address: String?) : NaviEvent

    data class CurrentLocationReceived(val location: NaviLocation) : NaviEvent

    data class RecentDestinationsReceived(val destinations: List<RecentDestination>) : NaviEvent

    data class Error(val code: String) : NaviEvent
}
