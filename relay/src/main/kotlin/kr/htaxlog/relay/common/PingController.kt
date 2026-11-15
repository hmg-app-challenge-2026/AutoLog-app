package kr.htaxlog.relay.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PingController {
    @GetMapping("/v1/ping")
    fun ping() = mapOf("service" to "htaxlog-relay", "ok" to true)
}
