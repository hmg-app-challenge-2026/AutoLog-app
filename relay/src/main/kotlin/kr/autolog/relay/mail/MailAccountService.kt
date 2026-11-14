package kr.autolog.relay.mail

import kr.autolog.relay.admin.ReferenceRepository
import kr.autolog.relay.common.ApiException
import kr.autolog.relay.device.DeviceRegisteredListener
import kr.autolog.relay.inbox.InboxService
import kr.autolog.shared.MailAccountPayload
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** PUT /admin/mail-account 본문. [password]는 새로 넣을 때만 — 비우면 저장된 비밀번호를 그대로 쓴다. */
data class MailAccountRequest(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val fromName: String? = null,
    val startTls: Boolean? = null,
)

/** 관리자 웹에 보이는 계정. 비밀번호는 절대 돌려주지 않는다. */
data class MailAccountView(
    val host: String,
    val port: Int,
    val username: String,
    val fromName: String?,
    val startTls: Boolean,
    val updatedAt: Instant,
)

/**
 * 회사 메일 발신 계정 (F-24, F-54). 회사마다 하나, 차의 메일 계정도 하나다 — 여기서 정하면 차의 계정을 덮어쓴다.
 *
 * - 저장: SMTP 로그인을 해 보고 성공해야 저장한다. 비밀번호는 [MailSecret]으로 암호화.
 * - 배포: 저장·삭제하면 회사의 모든 차 inbox에 MAIL_ACCOUNT. 새 차가 등록되면 그 차에도.
 * - inbox 표에는 비밀번호 없이 넣고, 차가 가져갈 때([MailAccountInboxRefresher]) 그 순간의 계정으로 채운다 — DB에 평문을 남기지 않는다.
 * - 로그에는 서버·포트만 남긴다(주소·비밀번호 X, CONVENTIONS 7.6).
 */
@Service
class MailAccountService(
    private val repo: MailAccountRepository,
    private val refs: ReferenceRepository,
    private val inbox: InboxService,
    private val secret: MailSecret,
    private val verifier: SmtpVerifier,
    private val clock: Clock,
) : DeviceRegisteredListener {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(org: UUID): MailAccountView? = repo.find(org)?.toView()

    fun save(org: UUID, req: MailAccountRequest): MailAccountView {
        val old = repo.find(org)
        val host = req.host?.trim()?.lowercase().orEmpty().ifEmpty { throw invalid("SMTP 서버를 입력해 주세요") }
        if (!HOST.matches(host)) throw invalid("SMTP 서버 주소가 올바르지 않습니다")
        val port = req.port ?: DEFAULT_PORT
        if (port !in 1..MAX_PORT) throw invalid("포트가 올바르지 않습니다")
        val username = req.username?.trim().orEmpty().ifEmpty { throw invalid("메일 주소(아이디)를 입력해 주세요") }
        val password = req.password?.takeIf { it.isNotEmpty() }
            ?: old?.let { secret.decrypt(it.passwordEnc) }
            ?: throw invalid("앱 비밀번호를 입력해 주세요")
        val startTls = req.startTls ?: true
        val fromName = req.fromName?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FROM_NAME)
        secret.requireKey()

        when (verifier.verify(host, port, username, password, startTls)) {
            SmtpFailure.AUTH -> throw ApiException(
                HttpStatus.BAD_REQUEST,
                "MAIL_AUTH_FAILED",
                "로그인에 실패했습니다. 메일 주소와 앱 비밀번호를 확인해 주세요",
            )
            SmtpFailure.NETWORK -> throw ApiException(
                HttpStatus.BAD_REQUEST,
                "MAIL_CONNECT_FAILED",
                "메일 서버에 연결하지 못했습니다. 서버 주소와 포트를 확인해 주세요",
            )
            null -> Unit
        }
        val row =
            MailAccountRow(org, host, port, username, secret.encrypt(password), fromName, startTls, clock.instant())
        repo.upsert(row)
        publish(org)
        log.info("회사 메일 계정 저장 org={} {}:{}", org, host, port)
        return row.toView()
    }

    fun delete(org: UUID) {
        if (repo.delete(org)) {
            publish(org)
            log.info("회사 메일 계정 삭제 org={}", org)
        }
    }

    override fun onRegistered(vehicleId: UUID, orgId: UUID) {
        if (repo.find(orgId) != null) inbox.deliver(vehicleId, letter(orgId))
    }

    private fun publish(org: UUID) {
        val payload = letter(org)
        refs.vehicleIds(org).forEach { inbox.deliver(it, payload) }
    }

    /** inbox 표에 넣을 편지 — 비밀번호는 빼고. */
    private fun letter(org: UUID) = MailAccountPayload(repo.find(org)?.toDto(password = null))

    private fun MailAccountRow.toView() = MailAccountView(host, port, username, fromName, startTls, updatedAt)

    private fun invalid(message: String) = ApiException(HttpStatus.BAD_REQUEST, "MAIL_ACCOUNT_INVALID", message)

    private companion object {
        const val DEFAULT_PORT = 587
        const val MAX_PORT = 65535
        const val MAX_FROM_NAME = 60
        val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$")
    }
}

internal fun MailAccountRow.toDto(password: String?) =
    kr.autolog.shared.MailAccountDto(host, port, username, password, fromName, startTls)
