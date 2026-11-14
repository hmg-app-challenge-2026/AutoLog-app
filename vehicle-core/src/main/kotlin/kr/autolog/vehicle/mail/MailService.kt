package kr.autolog.vehicle.mail

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kr.autolog.shared.MailAccountDto
import kr.autolog.vehicle.core.MailApi
import kr.autolog.vehicle.core.MailError
import kr.autolog.vehicle.core.MailResult
import kr.autolog.vehicle.core.ReportFile
import kr.autolog.vehicle.core.SmtpConfig

/**
 * [MailApi] 구현 (F-22, F-24).
 *
 * 오프라인·실패 시 "전송 대기" 큐는 reports.sendState(앱 A의 Room)에 둔다. 이 클래스는 한 번 보내 보고 결과만 돌려준다.
 * 로그에는 수신자 수와 결과만 남기고 주소·비밀번호는 남기지 않는다(CONVENTIONS 7.6).
 *
 * 발신 계정은 하나다. 차 설정 화면에서 넣든 관리자 웹에서 정해 inbox로 오든 같은 자리를 덮어쓴다.
 */
class MailService(
    private val store: SmtpCredentialStore,
    private val mailer: SmtpMailer = SmtpMailer(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : MailApi {
    private val _account = MutableStateFlow(store.load()?.username)
    override val account: StateFlow<String?> = _account.asStateFlow()

    override suspend fun send(report: ReportFile, to: List<String>): MailResult {
        val config = store.load() ?: return MailResult.Failed(MailError.NOT_CONFIGURED, "메일 계정을 먼저 설정해 주세요")
        if (to.isEmpty()) return MailResult.Failed(MailError.INVALID_RECIPIENT, "받는 사람을 선택해 주세요")
        if (!report.file.isFile) return MailResult.Failed(MailError.UNKNOWN, "운행기록부 파일이 없습니다")
        val mail =
            OutgoingMail(
                to = to,
                subject = MailComposer.subject(report),
                body = MailComposer.body(report),
                attachment = report.file,
                attachmentName = MailComposer.attachmentName(report),
                attachmentMime = MailComposer.XLSX_MIME,
            )
        val result = withContext(io) { mailer.send(config, mail) }
        log("운행기록부 ${report.month} 발송 수신자 ${to.size}명 → ${result.describe()}")
        return result
    }

    override suspend fun saveSmtp(config: SmtpConfig): MailResult {
        val result = withContext(io) { mailer.verify(config) }
        if (result == MailResult.Ok) {
            store.save(config)
            _account.value = config.username
        }
        log("SMTP 설정 확인 ${config.host}:${config.port} → ${result.describe()}")
        return result
    }

    override fun smtpConfigured(): Boolean = store.load() != null

    /**
     * 관리자 웹에서 정한 메일 계정을 받는다(inbox MAIL_ACCOUNT, 앱 A의 InboxSync가 부른다). null이면 계정을 지운다.
     * 관리자 웹이 저장할 때 접속을 확인했으므로 여기서 다시 확인하지 않는다. 비밀번호가 빠진 편지는 반영하지 않는다(false).
     */
    fun applyServerAccount(dto: MailAccountDto?): Boolean {
        if (dto == null) {
            store.clear()
        } else {
            val password = dto.password?.takeIf { it.isNotEmpty() } ?: return false
            store.save(SmtpConfig(dto.host, dto.port, dto.username, password, dto.fromName, dto.startTls))
        }
        _account.value = dto?.username
        log("관리자 웹 메일 계정 ${if (dto == null) "지움" else "받음 ${dto.host}:${dto.port}"}")
        return true
    }

    private fun MailResult.describe(): String = when (this) {
        MailResult.Ok -> "OK"
        is MailResult.Failed -> error.name
    }

    private companion object {
        const val TAG = "AutoLog/mail"
    }
}
