package kr.autolog.relay.admin

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.common.Tokens
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class LoginResult(val token: String, val expiresAt: Instant, val email: String, val orgName: String)

/**
 * 관리자 인증 (2026-11-13 회사별 계정). 회사마다 관리자가 이메일·비밀번호로 로그인([login])하면 세션 토큰을 준다.
 * 운영 웹은 그 토큰을 `X-Admin-Token` 헤더로 보내고, [authorize]가 그 관리자의 회사 id를 돌려준다 —
 * 모든 /admin API는 이 회사 id로만 조회·변경하므로 다른 회사 데이터는 보이지 않는다.
 * 회사는 운영 웹에서 직접 가입한다([signup] — 회사 + 첫 관리자, 2026-11-13 사용자 결정 "가입하면 바로 사용").
 * 운영자 /ops API(`ops/OpsController`)는 비밀번호 재설정 같은 예외 처리용.
 */
@Component
class AdminAuth(
    private val accounts: AdminAccountRepository,
    private val props: RelayProperties,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    /** 최근 회사 가입 시각(서버 전체). 자동 대량 가입을 막는 단순 상한. */
    private val signups = java.util.concurrent.ConcurrentLinkedDeque<Instant>()

    /** 이메일별 최근 로그인 실패 시각. 서버 1대 기준 메모리 보관(재시작하면 초기화). */
    private val failures = ConcurrentHashMap<String, List<Instant>>()

    fun authorize(token: String?): UUID = session(token).orgId

    fun session(token: String?): AdminSession {
        if (token.isNullOrBlank()) throw forbidden()
        return accounts.session(Tokens.sha256Hex(token), clock.instant()) ?: throw forbidden()
    }

    fun login(rawEmail: String, password: String): LoginResult {
        val email = normalizeEmail(rawEmail)
        val now = clock.instant()
        val recent = recentFailures(email, now)
        if (recent.size >= MAX_FAILURES) {
            throw ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_LOCKED",
                "로그인을 여러 번 실패했습니다. ${LOCK_WINDOW.toMinutes()}분 뒤에 다시 시도해 주세요",
            )
        }
        val admin = accounts.adminByEmail(email)
        // 계정이 없어도 같은 시간이 걸리게 가짜 해시로 한 번 검증한다(이메일 존재 여부 추측 방지)
        val ok = Passwords.verify(password, admin?.passwordHash ?: DUMMY_HASH) && admin != null
        if (!ok) {
            failures[email] = recent + now
            throw ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "이메일이나 비밀번호가 틀렸습니다")
        }
        failures.remove(email)
        val token = Tokens.newToken()
        val expiresAt = now.plus(Duration.ofHours(props.admin.sessionHours))
        val hash = Tokens.sha256Hex(token)
        accounts.insertSession(hash, admin!!.adminId, admin.orgId, expiresAt)
        val s = accounts.session(hash, now) ?: throw forbidden()
        return LoginResult(token, expiresAt, s.email, s.orgName)
    }

    /**
     * 회사 가입: 회사와 첫 관리자를 한 번에 만들고 바로 로그인한다. 가입하면 바로 쓸 수 있다(승인·메일 인증 없음).
     * 회사 이름·이메일은 서버 전체에서 하나뿐이다(같으면 409).
     */
    fun signup(rawOrgName: String, rawEmail: String, password: String): LoginResult {
        val orgName = rawOrgName.trim()
        val email = normalizeEmail(rawEmail)
        if (orgName.isEmpty()) throw bad("ORG_NAME_EMPTY", "회사 이름을 입력해 주세요")
        if (orgName.length > MAX_ORG_NAME) throw bad("ORG_NAME_LONG", "회사 이름은 ${MAX_ORG_NAME}자까지 쓸 수 있습니다")
        checkNewAdmin(email, password)
        val now = clock.instant()
        synchronized(signups) {
            while (signups.peekFirst()?.isBefore(now.minus(SIGNUP_WINDOW)) == true) signups.pollFirst()
            if (signups.size >= props.admin.signupsPerHour) {
                throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "SIGNUP_LIMIT", "가입 요청이 많습니다. 잠시 뒤에 다시 시도해 주세요")
            }
            signups.addLast(now)
        }
        if (accounts.orgNameTaken(orgName)) throw conflict("ORG_EXISTS", "이미 가입한 회사 이름입니다")
        if (accounts.emailTaken(email)) throw conflict("ADMIN_EXISTS", "이미 가입한 이메일입니다. 로그인해 주세요")
        try {
            tx.executeWithoutResult {
                val orgId = accounts.insertOrg(orgName)
                accounts.insertAdmin(orgId, email, Passwords.hash(password))
            }
        } catch (e: DuplicateKeyException) {
            // 동시에 같은 이름·이메일로 가입한 경우
            throw conflict("SIGNUP_CONFLICT", "이미 가입한 회사 이름이나 이메일입니다")
        }
        return login(email, password)
    }

    /** 새 관리자 이메일·비밀번호 규칙(가입·운영자 공통). 틀리면 400. */
    fun checkNewAdmin(email: String, password: String) {
        if (!EMAIL.matches(email)) throw bad("EMAIL_INVALID", "이메일 형식이 아닙니다")
        Passwords.problem(password)?.let { throw bad("PASSWORD_WEAK", it) }
    }

    fun logout(token: String?) {
        if (!token.isNullOrBlank()) accounts.deleteSession(Tokens.sha256Hex(token))
    }

    /** 로그인한 관리자가 자기 비밀번호를 바꾼다. 지금 세션만 남기고 다른 기기의 세션은 끊는다. */
    fun changePassword(token: String?, current: String, next: String) {
        val s = session(token)
        val admin = accounts.adminById(s.adminId) ?: throw forbidden()
        if (!Passwords.verify(current, admin.passwordHash)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_WRONG", "지금 비밀번호가 틀렸습니다")
        }
        Passwords.problem(next)?.let { throw ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_WEAK", it) }
        accounts.updatePassword(admin.adminId, Passwords.hash(next))
        accounts.deleteSessions(admin.adminId, keep = Tokens.sha256Hex(token!!))
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Seoul")
    fun purgeExpiredSessions() {
        accounts.deleteExpiredSessions(clock.instant())
        val now = clock.instant()
        failures.keys.forEach { email -> if (recentFailures(email, now).isEmpty()) failures.remove(email) }
    }

    private fun recentFailures(email: String, now: Instant): List<Instant> =
        failures[email].orEmpty().filter { it.isAfter(now.minus(LOCK_WINDOW)) }

    private fun bad(code: String, message: String) = ApiException(HttpStatus.BAD_REQUEST, code, message)

    private fun conflict(code: String, message: String) = ApiException(HttpStatus.CONFLICT, code, message)

    private fun forbidden() = ApiException(HttpStatus.FORBIDDEN, "ADMIN_FORBIDDEN", "로그인이 필요합니다. 다시 로그인해 주세요")

    companion object {
        const val MAX_FAILURES = 5
        const val MAX_ORG_NAME = 60
        val SIGNUP_WINDOW: Duration = Duration.ofHours(1)
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val LOCK_WINDOW: Duration = Duration.ofMinutes(10)
        private val DUMMY_HASH = Passwords.hash("autolog-dummy-password")

        fun normalizeEmail(email: String): String = email.trim().lowercase()
    }
}
