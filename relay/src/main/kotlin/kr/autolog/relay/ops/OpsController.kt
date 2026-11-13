package kr.autolog.relay.ops

import kr.autolog.relay.admin.AdminAccountRepository
import kr.autolog.relay.admin.AdminAuth
import kr.autolog.relay.admin.AdminSummary
import kr.autolog.relay.admin.Organization
import kr.autolog.relay.admin.Passwords
import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

data class CreateOrgRequest(val name: String)

data class CreateOrgResponse(val orgId: UUID, val name: String)

data class CreateAdminRequest(val email: String, val password: String)

data class CreateAdminResponse(val adminId: UUID, val orgId: UUID, val email: String)

data class ResetPasswordRequest(val password: String)

/**
 * 운영자(서비스 운영 측) API: 회사와 회사 관리자 계정을 만든다. 회사 관리자는 이걸 쓰지 않는다.
 * 인증은 `X-Ops-Token` = 환경변수 OPS_TOKEN(비어 있으면 전부 403).
 * nginx가 /ops를 밖으로 내보내지 않으므로 서버 안(relay 8080)에서만 부른다 — `scripts/ops.sh`.
 */
@RestController
@RequestMapping("/ops")
class OpsController(
    private val accounts: AdminAccountRepository,
    private val auth: AdminAuth,
    private val props: RelayProperties,
) {
    @GetMapping("/orgs")
    fun orgs(@RequestHeader(HEADER, required = false) t: String?): List<Organization> {
        authorize(t)
        return accounts.orgs()
    }

    @PostMapping("/orgs")
    fun createOrg(
        @RequestHeader(HEADER, required = false) t: String?,
        @RequestBody body: CreateOrgRequest,
    ): CreateOrgResponse {
        authorize(t)
        val name = body.name.trim()
        if (name.isEmpty()) throw bad("ORG_NAME_EMPTY", "회사 이름을 입력해 주세요")
        if (accounts.orgNameTaken(name)) throw ApiException(HttpStatus.CONFLICT, "ORG_EXISTS", "이미 있는 회사 이름입니다")
        return CreateOrgResponse(accounts.insertOrg(name), name)
    }

    @GetMapping("/orgs/{orgId}/admins")
    fun admins(@RequestHeader(HEADER, required = false) t: String?, @PathVariable orgId: UUID): List<AdminSummary> {
        authorize(t)
        return accounts.admins(orgId)
    }

    @PostMapping("/orgs/{orgId}/admins")
    fun createAdmin(
        @RequestHeader(HEADER, required = false) t: String?,
        @PathVariable orgId: UUID,
        @RequestBody body: CreateAdminRequest,
    ): CreateAdminResponse {
        authorize(t)
        if (!accounts.orgExists(orgId)) throw ApiException(HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "회사가 없습니다")
        val email = AdminAuth.normalizeEmail(body.email)
        auth.checkNewAdmin(email, body.password)
        if (accounts.emailTaken(email)) throw ApiException(HttpStatus.CONFLICT, "ADMIN_EXISTS", "이미 있는 관리자 이메일입니다")
        return CreateAdminResponse(accounts.insertAdmin(orgId, email, Passwords.hash(body.password)), orgId, email)
    }

    /** 관리자가 비밀번호를 잊었을 때 운영자가 새로 정해 준다. 그 관리자의 로그인은 모두 끊긴다. */
    @PostMapping("/admins/{adminId}/password")
    fun resetPassword(
        @RequestHeader(HEADER, required = false) t: String?,
        @PathVariable adminId: UUID,
        @RequestBody body: ResetPasswordRequest,
    ): ResponseEntity<Unit> {
        authorize(t)
        Passwords.problem(body.password)?.let { throw bad("PASSWORD_WEAK", it) }
        if (!accounts.updatePassword(adminId, Passwords.hash(body.password))) {
            throw ApiException(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "관리자가 없습니다")
        }
        accounts.deleteSessions(adminId)
        return ResponseEntity.noContent().build()
    }

    private fun authorize(token: String?) {
        val expected = props.ops.token
        val ok =
            expected.isNotEmpty() && token != null && MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())
        if (!ok) throw ApiException(HttpStatus.FORBIDDEN, "OPS_FORBIDDEN", "운영자 토큰이 없거나 틀렸습니다")
    }

    private fun bad(code: String, message: String) = ApiException(HttpStatus.BAD_REQUEST, code, message)

    private companion object {
        const val HEADER = "X-Ops-Token"
    }
}
