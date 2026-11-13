package kr.autolog.vehicle.fake

import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.GeocodeApi
import kr.autolog.vehicle.core.GeocodeResult

/**
 * [GeocodeApi] 가짜. 서버(카카오)가 늘 [result]를 준다고 흉내 낸다. 서버 실패를 보려면 [serverDown] = true:
 * 지금 위치([isCurrentPosition])면 [naviAddress]로 대신하고(내비 폴백), 지난 좌표면 null.
 */
class FakeGeocode(
    var result: GeocodeResult? = SAMPLE,
    var serverDown: Boolean = false,
    var naviAddress: String? = FakeTripSession.SAMPLE_ORIGIN_ADDRESS,
) : GeocodeApi {
    /** 불린 좌표 기록(테스트 확인용). */
    val calls = mutableListOf<Pair<Double, Double>>()

    override suspend fun reverse(lat: Double, lng: Double, isCurrentPosition: Boolean): GeocodeResult? {
        calls += lat to lng
        if (!serverDown) return result
        return naviAddress?.takeIf { isCurrentPosition }?.let { GeocodeResult(it, null, AddressSource.NAVI_HELPER) }
    }

    companion object {
        val SAMPLE = GeocodeResult(FakeTripSession.SAMPLE_ORIGIN_ADDRESS, "연희빌딩", AddressSource.KAKAO_RELAY)
    }
}
