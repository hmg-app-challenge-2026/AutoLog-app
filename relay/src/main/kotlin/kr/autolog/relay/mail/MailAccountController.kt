package kr.autolog.relay.mail

import kr.autolog.relay.admin.AdminAuth
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회사 메일 발신 계정 (운영 웹 [메일 설정]). 인증은 X-Admin-Token.
 * GET은 계정이 없으면 204. PUT은 SMTP 로그인을 확인한 뒤 저장하고 회사의 모든 차에 보낸다.
 */
@RestController
@RequestMapping("/admin/mail-account")
class MailAccountController(private val auth: AdminAuth, private val service: MailAccountService) {
    @GetMapping
    fun get(@RequestHeader(TOKEN, required = false) t: String?): ResponseEntity<MailAccountView> =
        service.get(auth.authorize(t))?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PutMapping
    fun put(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody body: MailAccountRequest) =
        service.save(auth.authorize(t), body)

    @DeleteMapping
    fun delete(@RequestHeader(TOKEN, required = false) t: String?): ResponseEntity<Unit> {
        service.delete(auth.authorize(t))
        return ResponseEntity.noContent().build()
    }

    private companion object {
        const val TOKEN = "X-Admin-Token"
    }
}
