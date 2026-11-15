package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kr.htaxlog.vehicle.core.MailApi
import kr.htaxlog.vehicle.core.MailError
import kr.htaxlog.vehicle.core.MailResult
import kr.htaxlog.vehicle.core.ReportFile
import kr.htaxlog.vehicle.core.SmtpConfig

/** [MailApi] 가짜. 실제로 보내지 않고 [sent]에 쌓는다. 실패 화면은 [nextResult]를 바꿔서 본다. */
class FakeMailApi(
    configured: Boolean = true,
    var nextResult: MailResult = MailResult.Ok,
    private val sendMs: Long = 1_000,
) : MailApi {
    private var config: SmtpConfig? = if (configured) {
        SmtpConfig(
            "smtp.example.com",
            username = "me@example.com",
            password = "x",
        )
    } else {
        null
    }
    val sent = mutableListOf<Pair<ReportFile, List<String>>>()
    override val account = MutableStateFlow(config?.username)

    override suspend fun send(report: ReportFile, to: List<String>): MailResult {
        if (config == null) return MailResult.Failed(MailError.NOT_CONFIGURED, "메일 설정이 필요합니다")
        if (to.isEmpty()) return MailResult.Failed(MailError.INVALID_RECIPIENT, "받는 사람이 없습니다")
        delay(sendMs)
        if (nextResult == MailResult.Ok) sent += report to to
        return nextResult
    }

    override suspend fun saveSmtp(config: SmtpConfig): MailResult {
        this.config = config
        account.value = config.username
        return MailResult.Ok
    }

    override fun smtpConfigured(): Boolean = config != null
}
