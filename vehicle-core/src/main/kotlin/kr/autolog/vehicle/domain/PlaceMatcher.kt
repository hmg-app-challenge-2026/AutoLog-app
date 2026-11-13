package kr.autolog.vehicle.domain

import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.GeoDistance

/** 등록 장소. Room PlaceEntity에서 필요한 값만 옮긴 것. */
data class Place(
    val placeId: String,
    val label: String,
    val kind: PlaceKind,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 150,
)

/**
 * 좌표를 등록 장소에 매칭한다 (F-06 목적지 미설정, F-07 출발지).
 *
 * Fused Location에는 accuracy가 없어서(sdk-findings.md) 반경에 고정 여유 [marginM]을 더한다(기술명세 6장 대체안).
 * 반경 안에 여러 곳이 있으면 가장 가까운 곳.
 */
object PlaceMatcher {
    const val DEFAULT_MARGIN_M = 50.0

    fun match(lat: Double, lng: Double, places: List<Place>, marginM: Double = DEFAULT_MARGIN_M): Place? = places
        .map { it to distanceM(lat, lng, it.lat, it.lng) }
        .filter { (place, d) -> d <= place.radiusM + marginM }
        .minByOrNull { (_, d) -> d }
        ?.first

    /** 두 좌표 사이 거리(m), haversine. */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        GeoDistance.meters(lat1, lng1, lat2, lng2)
}
