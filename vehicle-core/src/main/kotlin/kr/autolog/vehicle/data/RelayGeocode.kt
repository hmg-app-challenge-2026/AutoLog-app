package kr.autolog.vehicle.data

import kr.autolog.shared.KoreanAddress
import kr.autolog.shared.ReverseGeocodeResponse
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.ContextApi
import kr.autolog.vehicle.core.GeocodeApi
import kr.autolog.vehicle.core.GeocodeResult

/**
 * [GeocodeApi] 실구현 (F-06, F-07, open-issues I-05).
 * ① 중계 서버 `GET /v1/geo/reverse`(카카오) → ② [isCurrentPosition]이면 내비 현재 위치 주소(광역 이름만 줄임) → ③ null.
 * 내비는 좌표 → 주소를 못 하므로 지난 좌표에는 ②를 쓰지 않는다. 서버 미등록·꺼짐·키 없음(503)도 ②로 넘어간다.
 * [relay]는 `DeviceRepository.call { reverseGeocode(...) }` — 테스트에서 바꿔 끼우려고 함수로 받는다.
 */
class RelayGeocode(
    private val relay: suspend (lat: Double, lng: Double) -> RelayResult<ReverseGeocodeResponse>,
    private val context: ContextApi,
    private val log: (String) -> Unit = {},
) : GeocodeApi {
    override suspend fun reverse(lat: Double, lng: Double, isCurrentPosition: Boolean): GeocodeResult? {
        when (val r = relay(lat, lng)) {
            is RelayResult.Ok -> r.value.address?.takeIf { it.isNotBlank() }?.let {
                return GeocodeResult(it, r.value.placeName?.takeIf(String::isNotBlank), AddressSource.KAKAO_RELAY)
            }
            is RelayResult.Failure -> log("geocode relay failed: ${r.code ?: r.status ?: "network"}")
        }
        if (!isCurrentPosition) return null
        val navi = context.currentNaviLocation() ?: return null // ContextApi는 예외 대신 null
        return KoreanAddress.shortenRegion(navi.address)?.let { GeocodeResult(it, null, AddressSource.NAVI_HELPER) }
    }
}
