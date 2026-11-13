package kr.autolog.relay.geo

import kr.autolog.relay.common.ApiException
import kr.autolog.shared.ReverseGeocodeResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

class GeoServiceTest {
    private var next: ReverseLookup = ReverseLookup.Found(ReverseGeocodeResponse("서울 중구 태평로1가 세종대로 110", "서울특별시청"))
    private var calls = 0
    private val service = GeoService { _, _ -> calls++.let { next } }

    @Test
    fun `같은 곳(소수 4자리)은 카카오를 한 번만 부른다`() {
        val a = service.reverse(37.56661, 126.97801)
        val b = service.reverse(37.56659, 126.97799)

        assertEquals(a, b)
        assertEquals(1, calls)
    }

    @Test
    fun `주소를 모르면 빈 응답이고 그것도 캐시한다`() {
        next = ReverseLookup.NotFound

        assertEquals(ReverseGeocodeResponse(), service.reverse(33.0, 125.0))
        service.reverse(33.0, 125.0)
        assertEquals(1, calls)
    }

    @Test
    fun `꺼져 있으면 503, 카카오 실패면 502이고 실패는 캐시하지 않는다`() {
        next = ReverseLookup.Disabled
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows<ApiException> { service.reverse(37.0, 127.0) }.status)

        next = ReverseLookup.Failed
        val e = assertThrows<ApiException> { service.reverse(37.0, 127.0) }
        assertEquals(HttpStatus.BAD_GATEWAY, e.status)
        assertEquals("GEOCODER_UNAVAILABLE", e.code)
        assertEquals(2, calls)
    }

    @Test
    fun `좌표 범위를 벗어나면 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ApiException> { service.reverse(91.0, 0.0) }.status)
        assertEquals(0, calls)
    }
}
