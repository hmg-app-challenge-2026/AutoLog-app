package kr.autolog.relay.geo

import kr.autolog.relay.device.DeviceService
import kr.autolog.shared.ReverseGeocodeResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GeoController(private val devices: DeviceService, private val geo: GeoService) {
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
}
