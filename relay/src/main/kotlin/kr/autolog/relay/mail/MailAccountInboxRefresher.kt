package kr.autolog.relay.mail

import kr.autolog.relay.inbox.InboxPayloadRefresher
import kr.autolog.shared.InboxPayload
import kr.autolog.shared.MailAccountPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 차가 inbox를 가져갈 때 MAIL_ACCOUNT 편지를 **그 순간의** 회사 계정(비밀번호 포함)으로 바꾼다.
 * inbox 표에는 비밀번호 없는 편지만 있다. 계정이 그사이 지워졌으면 null 편지(차도 지운다).
 * InboxService가 이 클래스를 주입받으므로 InboxService에 의존하지 않는다(순환 방지).
 */
@Component
class MailAccountInboxRefresher(private val repo: MailAccountRepository, private val secret: MailSecret) :
    InboxPayloadRefresher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun refresh(vehicleId: UUID, payload: InboxPayload): InboxPayload {
        if (payload !is MailAccountPayload || payload.account == null) return payload
        val row = repo.findByVehicle(vehicleId) ?: return MailAccountPayload(null)
        val password = secret.decrypt(row.passwordEnc)
        if (password == null) log.warn("회사 메일 비밀번호를 풀 수 없음 — MAIL_SECRET_KEY가 바뀌었나? org={}", row.orgId)
        return MailAccountPayload(row.toDto(password))
    }
}
