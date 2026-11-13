package kr.autolog.vehicle.session

import kr.autolog.shared.KoreanAddress
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.DestinationInfo
import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.TripFacts
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.TripEntity

/** Room 운행 → 화면용 진행 중 운행. */
internal fun TripEntity.toLive() = LiveTrip(
    tripId = tripId,
    startTime = startTime,
    startOdometerKm = startOdometerKm,
    destinationLabel = destinationLabel,
    driverUserId = userId,
    originLabel = originLabel,
    originAddress = originAddress,
    destinationAddress = destinationAddress,
)

/** 진행 중 운행 표시값의 장소만 Room 최신 값으로 바꾼다(주소는 비동기로 늦게 온다). */
internal fun LiveTrip.withPlacesOf(t: TripEntity) = copy(
    originLabel = t.originLabel,
    originAddress = t.originAddress,
    destinationLabel = t.destinationLabel,
    destinationAddress = t.destinationAddress,
)

/** Room 운행 → 앱 B 추천 엔진 입력(경계 타입). */
internal fun TripEntity.toFacts() = TripFacts(
    tripId = tripId,
    vehicleId = vehicleId,
    startTime = startTime,
    endTime = endTime,
    startLat = originLat,
    startLng = originLng,
    endLat = destinationLat,
    endLng = destinationLng,
    destinationLabel = destinationLabel,
    driverUserId = userId,
)

/**
 * 내비 목적지를 운행에 붙인다(F-06). 마지막 값이 남는다. 내비가 준 목적지 주소도 남겨 두고(서버에 닿지 않을 때 폴백),
 * 운행이 끝나면 실제 도착 위치로 다시 정한다([PlacePolicy.mergeDestination]).
 */
internal fun TripEntity.withDestination(d: DestinationInfo): TripEntity {
    val address = KoreanAddress.shortenRegion(d.address)
    return copy(
        destinationLabel = d.label,
        destinationLat = d.lat,
        destinationLng = d.lng,
        destinationSource = PlaceSource.NAVI_HELPER,
        destinationAddress = address,
        destinationAddressSource = address?.let { AddressSource.NAVI_HELPER },
    )
}
