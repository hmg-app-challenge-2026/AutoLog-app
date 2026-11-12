package kr.autolog.vehicle.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.currentMonth
import kr.autolog.vehicle.core.MailResult
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.ReportFile
import kr.autolog.vehicle.data.repository.BuildOutcome
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.data.repository.ReportRepository
import kr.autolog.vehicle.data.repository.SettingsRepository
import kr.autolog.vehicle.data.repository.TripRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route
import java.time.YearMonth

/**
 * 운행기록부 내보내기(스펙 7.7). 주 버튼 하나가 단계를 따라간다: 만들기 → 보내기(실패면 다시 보내기) → 완료.
 * 만든 파일은 그 달에만 묶인다 — 달을 바꾸면 처음(만들기)부터. 상태 계산은 [reduceReport].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val reports: ReportRepository,
    private val trips: TripRepository,
    private val settings: SettingsRepository,
    private val navigator: Navigator,
    private val clock: AppClock,
) : ViewModel() {
    private val month = MutableStateFlow(clock.currentMonth())
    private val step = MutableStateFlow<ReportStep>(ReportStep.Ready)
    private val deselected = MutableStateFlow<Set<String>>(emptySet())
    private val smtpConfigured = MutableStateFlow(reports.smtpConfigured())
    private var built: ReportFile? = null

    private val summary =
        month.flatMapLatest { m -> trips.summary(m).onStart<MonthSummary?> { emit(null) } }

    val uiState: StateFlow<ReportUiState> =
        combine(
            combine(month, summary, reports.recipients(), deselected, ::Inputs),
            step,
            smtpConfigured,
            settings.link,
            settings.upload,
        ) { inputs, step, smtp, link, upload ->
            reduceReport(
                inputs.month, clock.currentMonth(), inputs.summary, inputs.recipients, inputs.deselected,
                step, smtp, link, upload,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState(month.value))

    fun onAction(action: ReportAction) {
        when (action) {
            ReportAction.PrevMonth -> changeMonth { it.minusMonths(1) }
            ReportAction.NextMonth -> changeMonth { if (it < clock.currentMonth()) it.plusMonths(1) else it }
            is ReportAction.ToggleRecipient ->
                deselected.update { if (action.email in it) it - action.email else it + action.email }
            ReportAction.Primary -> primary()
            ReportAction.Resume -> smtpConfigured.value = reports.smtpConfigured()
            ReportAction.Back -> navigator.pop()
        }
    }

    private fun changeMonth(change: (YearMonth) -> YearMonth) {
        if (busy()) return
        month.update(change)
        built = null
        step.value = ReportStep.Ready
    }

    private fun primary() {
        when (step.value) {
            ReportStep.Ready, is ReportStep.BuildFailed -> build()
            // 메일 계정이 없으면 보내기 대신 설정으로(돌아오면 Resume이 다시 읽는다)
            ReportStep.Built, is ReportStep.SendFailed -> if (smtpConfigured.value) {
                send()
            } else {
                navigator.push(
                    Route.Settings,
                )
            }
            is ReportStep.Sent -> navigator.pop()
            ReportStep.Building, ReportStep.Sending -> Unit
        }
    }

    private fun build() {
        step.value = ReportStep.Building
        viewModelScope.launch {
            step.value = when (val r = reports.build(month.value)) {
                is BuildOutcome.Built -> {
                    built = r.file
                    smtpConfigured.value = reports.smtpConfigured()
                    ReportStep.Built
                }
                is BuildOutcome.Failed -> ReportStep.BuildFailed(r.reason)
            }
        }
    }

    private fun send() {
        val file = built ?: return
        val to = uiState.value.recipients.filter { it.selected }.map { it.email }
        if (to.isEmpty()) return
        step.value = ReportStep.Sending
        viewModelScope.launch {
            step.value = when (val r = reports.send(file, to)) {
                MailResult.Ok -> ReportStep.Sent(to.size)
                is MailResult.Failed -> ReportStep.SendFailed(r.error)
            }
        }
    }

    private fun busy() = step.value == ReportStep.Building || step.value == ReportStep.Sending

    private data class Inputs(
        val month: YearMonth,
        val summary: MonthSummary?,
        val recipients: List<RecipientInfo>,
        val deselected: Set<String>,
    )
}
