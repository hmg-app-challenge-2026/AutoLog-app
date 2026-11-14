package kr.autolog.relay.purpose

import kr.autolog.relay.admin.AdminAuth
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 운영 웹 [목적] (F-08). 인증은 X-Admin-Token. 바꾸면 회사의 모든 차에 보낸다. */
@RestController
class PurposeController(private val auth: AdminAuth, private val service: PurposeService) {
    @GetMapping("/admin/purposes")
    fun purposes(@RequestHeader(TOKEN, required = false) t: String?) = service.purposes(auth.authorize(t))

    @PostMapping("/admin/purposes")
    fun addPurpose(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody body: PurposeRequest) =
        ResponseEntity.status(HttpStatus.CREATED).body(service.addPurpose(auth.authorize(t), body))

    @DeleteMapping("/admin/purposes/{id}")
    fun deletePurpose(
        @RequestHeader(TOKEN, required = false) t: String?,
        @PathVariable id: Long,
    ): ResponseEntity<Unit> {
        service.deletePurpose(auth.authorize(t), id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/admin/purpose-rules")
    fun rules(@RequestHeader(TOKEN, required = false) t: String?) = service.rules(auth.authorize(t))

    @PostMapping("/admin/purpose-rules")
    fun addRule(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody body: PurposeRuleRequest) =
        ResponseEntity.status(HttpStatus.CREATED).body(service.addRule(auth.authorize(t), body))

    @DeleteMapping("/admin/purpose-rules/{id}")
    fun deleteRule(@RequestHeader(TOKEN, required = false) t: String?, @PathVariable id: UUID): ResponseEntity<Unit> {
        service.deleteRule(auth.authorize(t), id)
        return ResponseEntity.noContent().build()
    }

    private companion object {
        const val TOKEN = "X-Admin-Token"
    }
}
