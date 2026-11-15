package kr.htaxlog.relay.inbox

import kr.htaxlog.relay.device.DeviceService
import kr.htaxlog.shared.InboxItem
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class InboxController(private val devices: DeviceService, private val inbox: InboxService) {
    /** 내 차 앞으로 온 편지 목록 (F-11, F-42). */
    @GetMapping("/v1/inbox")
    fun list(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<InboxItem> =
        inbox.pending(devices.authenticate(auth).vehicleId)

    /** 수신 확인 → 삭제. */
    @PostMapping("/v1/inbox/{id}/ack")
    fun ack(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable id: Long,
    ): ResponseEntity<Unit> {
        inbox.ack(devices.authenticate(auth).vehicleId, id)
        return ResponseEntity.noContent().build()
    }
}
