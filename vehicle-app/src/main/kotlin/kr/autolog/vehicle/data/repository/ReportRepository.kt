package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kr.autolog.vehicle.core.MailApi
import kr.autolog.vehicle.core.MailResult
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.ReportApi
import kr.autolog.vehicle.core.ReportBuildResult
import kr.autolog.vehicle.core.ReportFile
import kr.autolog.vehicle.core.SettingsApi
import java.time.YearMonth

/** 운행기록부 만들기·보내기(스펙 7.7, F-21·22). */
interface ReportRepository {
    suspend fun build(month: YearMonth): BuildOutcome

    suspend fun send(file: ReportFile, to: List<String>): MailResult

    fun smtpConfigured(): Boolean

    fun recipients(): Flow<List<RecipientInfo>>
}

class DefaultReportRepository(
    private val report: ReportApi,
    private val mail: MailApi,
    private val settings: SettingsApi,
) : ReportRepository {
    override suspend fun build(month: YearMonth): BuildOutcome = when (val r = report.build(month)) {
        is ReportBuildResult.Built -> BuildOutcome.Built(r.file)
        is ReportBuildResult.Failed -> BuildOutcome.Failed(r.reason)
    }

    override suspend fun send(file: ReportFile, to: List<String>): MailResult = mail.send(file, to)

    override fun smtpConfigured(): Boolean = mail.smtpConfigured()

    override fun recipients(): Flow<List<RecipientInfo>> = settings.recipients()
}
