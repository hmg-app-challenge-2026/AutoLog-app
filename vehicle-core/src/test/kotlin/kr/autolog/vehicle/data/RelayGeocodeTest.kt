package kr.autolog.vehicle.data

import kotlinx.coroutines.test.runTest
import kr.autolog.shared.ReverseGeocodeResponse
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.GeocodeResult
import kr.autolog.vehicle.core.NaviCurrentLocation
import kr.autolog.vehicle.fake.FakeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayGeocodeTest {
    private val ctx = FakeContext().apply { setNaviLocation(NaviCurrentLocation(37.4, 127.1, "경기도 성남시 분당구 판교역로 1")) }
    private var server: RelayResult<ReverseGeocodeResponse> = RelayResult.Ok(
        ReverseGeocodeResponse("경기 성남시 분당구 삼평동 판교역로 1", "판교빌딩"),
    )
    private val geocode = RelayGeocode({ _, _ -> server }, ctx)

    @Test
    fun `서버가 주소를 주면 카카오 주소와 건물 이름을 쓴다`() = runTest {
        assertEquals(
            GeocodeResult("경기 성남시 분당구 삼평동 판교역로 1", "판교빌딩", AddressSource.KAKAO_RELAY),
            geocode.reverse(37.4, 127.1, isCurrentPosition = true),
        )
    }

    @Test
    fun `서버에 닿지 않으면 지금 위치는 내비 주소를 줄여 쓴다`() = runTest {
        server = RelayResult.Failure(null, null, "timeout")

        assertEquals(
            GeocodeResult("경기 성남시 분당구 판교역로 1", null, AddressSource.NAVI_HELPER),
            geocode.reverse(37.4, 127.1, isCurrentPosition = true),
        )
    }

    @Test
    fun `서버 키가 없어도(503) 내비로 넘어간다`() = runTest {
        server = RelayResult.Failure(503, "GEOCODER_DISABLED", "off")

        assertEquals(AddressSource.NAVI_HELPER, geocode.reverse(37.4, 127.1, isCurrentPosition = true)?.source)
    }

    @Test
    fun `지난 좌표는 내비로 대신하지 않는다`() = runTest {
        server = RelayResult.Failure(null, null, "timeout")

        assertNull(geocode.reverse(37.4, 127.1, isCurrentPosition = false))
    }

    @Test
    fun `서버가 주소를 모르면 지금 위치는 내비 주소를 쓴다`() = runTest {
        server = RelayResult.Ok(ReverseGeocodeResponse())

        assertEquals(AddressSource.NAVI_HELPER, geocode.reverse(37.4, 127.1, isCurrentPosition = true)?.source)
        assertNull(geocode.reverse(37.4, 127.1, isCurrentPosition = false))
    }

    @Test
    fun `내비도 주소가 없으면 null`() = runTest {
        server = RelayResult.Failure(null, null, "timeout")
        ctx.setNaviLocation(null)

        assertNull(geocode.reverse(37.4, 127.1, isCurrentPosition = true))
    }
}
