package kr.htaxlog.relay.receipt

import kr.htaxlog.relay.device.DeviceService
import kr.htaxlog.shared.QrTokenResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class QrTokenController(private val devices: DeviceService, private val qr: QrTokenService) {
    /** 차가 [영수증 저장] 화면에서 QR 주소를 받는다 (F-30). */
    @PostMapping("/v1/qr-tokens")
    fun issue(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): QrTokenResponse =
        qr.issue(devices.authenticate(auth))
}
