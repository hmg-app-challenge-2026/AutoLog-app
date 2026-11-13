package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.GeoDistance
import kr.autolog.vehicle.core.GeocodeResult
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.TripEntity

/** 운행이 끝난 뒤 실제로 차가 있던 곳. 내비 현재 위치 또는 위치 SDK 좌표. */
internal data class EndPosition(val lat: Double, val lng: Double, val source: PlaceSource)

/**
 * 출발지·도착지 이름과 주소를 정하는 규칙 (F-06, F-07, open-issues I-05). 순수 함수 — 네트워크·DB는 세션이 한다.
 *
 * - 이름: 등록 장소 > (도착지만) 실제로 도착한 내비 목적지 > 건물 이름(카카오) > 없음. 사람이 고른 도착지 이름은 그대로.
 * - 주소: 서버(카카오) 주소가 오면 그것, 아니면 지금 있는 주소(내비)를 둔다. 카카오 주소를 내비 주소로 되돌리지 않는다.
 * - 거리·목적은 건드리지 않는다.
 */
internal object PlacePolicy {
    /** 운행이 끝난 곳이 내비 목적지에서 이 안이면 "목적지에 도착했다"로 보고 목적지 이름을 쓴다. */
    const val NAVI_ARRIVED_M = 300.0

    fun mergeOrigin(trip: TripEntity, place: PlaceInfo?, geo: GeocodeResult?): TripEntity {
        val (address, source) = pickAddress(trip.originAddress, trip.originAddressSource, geo)
        return trip.copy(
            originLabel = place?.label ?: trip.originLabel ?: geo?.placeName,
            originAddress = address,
            originAddressSource = source,
        )
    }

    /**
     * [end]는 운행이 끝난 직후의 실제 위치(모르면 null — 그러면 저장된 도착 좌표를 믿는다).
     * 내비 목적지를 설정했지만 다른 곳에서 끝냈으면 목적지 이름·주소·좌표를 실제 위치로 바꾼다.
     */
    fun mergeDestination(trip: TripEntity, end: EndPosition?, place: PlaceInfo?, geo: GeocodeResult?): TripEntity {
        if (trip.destinationSource == PlaceSource.USER_SELECTED) {
            val (address, source) = pickAddress(trip.destinationAddress, trip.destinationAddressSource, geo)
            return trip.copy(destinationAddress = address, destinationAddressSource = source)
        }
        val naviLat = trip.destinationLat.takeIf { trip.destinationSource == PlaceSource.NAVI_HELPER }
        val naviLng = trip.destinationLng.takeIf { trip.destinationSource == PlaceSource.NAVI_HELPER }
        val arrived = when {
            naviLat == null || naviLng == null -> false
            end == null -> true // 실제 위치를 모르면 내비 목적지를 믿는다
            else -> GeoDistance.meters(end.lat, end.lng, naviLat, naviLng) <= NAVI_ARRIVED_M
        }
        val missedNaviDestination = naviLat != null && !arrived
        val base =
            if (missedNaviDestination) {
                trip.copy(destinationLabel = null, destinationAddress = null, destinationAddressSource = null)
            } else {
                trip
            }
        val (address, source) = pickAddress(base.destinationAddress, base.destinationAddressSource, geo)
        return base.copy(
            destinationLabel = place?.label ?: base.destinationLabel ?: geo?.placeName,
            destinationLat = end?.lat ?: base.destinationLat,
            destinationLng = end?.lng ?: base.destinationLng,
            destinationSource = end?.source ?: base.destinationSource,
            destinationAddress = address,
            destinationAddressSource = source,
        )
    }

    /** 카카오 > 지금 값 > 내비 폴백. 이미 카카오 주소면 내비 주소로 되돌리지 않는다. */
    private fun pickAddress(
        current: String?,
        currentSource: AddressSource?,
        geo: GeocodeResult?,
    ): Pair<String?, AddressSource?> = when {
        geo == null -> current to currentSource
        geo.source == AddressSource.KAKAO_RELAY -> geo.address to geo.source
        currentSource == AddressSource.KAKAO_RELAY -> current to currentSource
        else -> geo.address to geo.source
    }
}
