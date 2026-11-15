package kr.htaxlog.vehicle.report

import android.content.Context
import kotlinx.coroutines.flow.first
import kr.htaxlog.vehicle.core.MailApi
import kr.htaxlog.vehicle.core.MailResult
import kr.htaxlog.vehicle.core.ReportApi
import kr.htaxlog.vehicle.core.ReportBuildResult
import kr.htaxlog.vehicle.core.SettingsApi
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 월말 자동 발송을 어느 달까지 했는지 기억한다. */
interface MonthlySentStore {
    fun lastSent(): YearMonth?

    fun save(month: YearMonth)
}

class PrefsMonthlySentStore(context: Context) : MonthlySentStore {
    private val prefs = context.getSharedPreferences("htaxlog_monthly", Context.MODE_PRIVATE)

    override fun lastSent(): YearMonth? = prefs.getString(KEY, null)?.let {
        runCatching { YearMonth.parse(it) }.getOrNull()
    }

    override fun save(month: YearMonth) {
        prefs.edit().putString(KEY, month.toString()).apply()
    }

    private companion object {
        const val KEY = "last_sent"
    }
}

/**
 * 월말 운행기록부 자동 생성·발송 (F-21 "월말 자동", 앱 B `SettingsApi.autoMonthlySend` 스위치).
 * 매달 1~7일에 지난달 것을 한 번 만든다. 차가 1일에 꺼져 있어도 그 주 안에 켜지면 보낸다.
 * 발송은 앱 B `MailApi`. 메일 설정·수신자가 없으면 보내지 않고 다음 기회에 다시 본다.
 */
class MonthlySender(
    private val settings: SettingsApi,
    private val report: ReportApi,
    private val mail: MailApi,
    private val sent: MonthlySentStore,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    private val log: (String) -> Unit = {},
) {
    sealed interface Outcome {
        data object NotDue : Outcome

        data class Waiting(val reason: String) : Outcome

        data class Sent(val month: YearMonth, val to: List<String>) : Outcome
    }

    suspend fun runOnce(): Outcome {
        val month = monthToSend(clock.millis(), zone, sent.lastSent(), settings.autoMonthlySend.value)
            ?: return Outcome.NotDue
        if (!mail.smtpConfigured()) return Outcome.Waiting("메일 계정 없음")
        val to = settings.recipients().first().map { it.email }
        if (to.isEmpty()) return Outcome.Waiting("수신자 없음")
        val file = when (val r = report.build(month)) {
            is ReportBuildResult.Built -> r.file
            is ReportBuildResult.Failed -> return Outcome.Waiting(r.reason)
        }
        return when (val m = mail.send(file, to)) {
            MailResult.Ok -> {
                sent.save(month)
                log("monthly report $month sent to ${to.size}")
                Outcome.Sent(month, to)
            }
            is MailResult.Failed -> Outcome.Waiting("발송 실패: ${m.error}")
        }
    }

    companion object {
        /** 매달 이 날짜까지 지난달 것을 보낼 기회가 있다. */
        const val CATCH_UP_DAYS = 7

        /** 지금 보내야 할 달. 꺼져 있거나, 기간이 지났거나, 이미 보냈으면 null. */
        fun monthToSend(now: Long, zone: ZoneId, lastSent: YearMonth?, enabled: Boolean): YearMonth? {
            if (!enabled) return null
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            if (today.dayOfMonth > CATCH_UP_DAYS) return null
            val previous = YearMonth.from(today).minusMonths(1)
            return previous.takeIf { lastSent == null || lastSent < it }
        }
    }
}
