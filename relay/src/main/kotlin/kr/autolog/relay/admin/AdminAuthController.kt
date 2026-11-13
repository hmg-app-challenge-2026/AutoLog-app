package kr.autolog.relay.admin

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class SignupRequest(val orgName: String, val email: String, val password: String)

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(val token: String, val expiresAt: Instant, val email: String, val orgName: String)

data class MeResponse(val email: String, val orgName: String)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** 운영 웹 로그인 (회사별 관리자, [AdminAuth]). 받은 token을 이후 요청의 `X-Admin-Token`에 넣는다. */
@RestController
@RequestMapping("/admin/auth")
class AdminAuthController(private val auth: AdminAuth) {
    @PostMapping("/login")
    fun login(@RequestBody body: LoginRequest): LoginResponse =
        auth.login(body.email, body.password).let { LoginResponse(it.token, it.expiresAt, it.email, it.orgName) }

    /** 회사 가입(회사 + 첫 관리자) → 바로 로그인한 것과 같은 응답. */
    @PostMapping("/signup")
    fun signup(@RequestBody body: SignupRequest): LoginResponse =
        auth.signup(body.orgName, body.email, body.password).let {
            LoginResponse(it.token, it.expiresAt, it.email, it.orgName)
        }

    @GetMapping("/me")
    fun me(@RequestHeader("X-Admin-Token", required = false) token: String?): MeResponse =
        auth.session(token).let { MeResponse(it.email, it.orgName) }

    @PostMapping("/logout")
    fun logout(@RequestHeader("X-Admin-Token", required = false) token: String?): ResponseEntity<Unit> {
        auth.logout(token)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/password")
    fun changePassword(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @RequestBody body: ChangePasswordRequest,
    ): ResponseEntity<Unit> {
        auth.changePassword(token, body.currentPassword, body.newPassword)
        return ResponseEntity.noContent().build()
    }
}
