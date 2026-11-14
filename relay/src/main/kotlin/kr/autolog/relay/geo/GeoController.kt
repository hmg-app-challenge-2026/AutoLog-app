package kr.autolog.relay.geo

import kr.autolog.relay.device.DeviceService
import kr.autolog.shared.PlaceSearchResponse
import kr.autolog.shared.ReverseGeocodeResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GeoController(
    private val devices: DeviceService,
    private val geo: GeoService,
    private val places: PlaceSearch,
    private val admins: kr.autolog.relay.admin.AdminAuth,
) {
    /** 차가 운행 시작·종료 직후 좌표를 주소로 바꾼다 (F-06, F-07). 등록된 차만. */
    @GetMapping("/v1/geo/reverse")
    fun reverse(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam lat: Double,
        @RequestParam lng: Double,
    ): ReverseGeocodeResponse {
        devices.authenticate(auth)
        return geo.reverse(lat, lng)
    }

    /** 차: 목적 규칙에 쓸 새 장소 찾기(F-08). 등록된 차만. */
    @GetMapping("/v1/geo/search")
    fun search(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam q: String,
    ): PlaceSearchResponse {
        devices.authenticate(auth)
        return places.search(q)
    }

    /** 운영 웹: [목적] 규칙의 새 장소 찾기. */
    @GetMapping("/admin/places/search")
    fun adminSearch(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @RequestParam q: String,
    ): PlaceSearchResponse {
        admins.authorize(token)
        return places.search(q)
    }
}
