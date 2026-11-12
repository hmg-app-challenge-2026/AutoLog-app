package kr.autolog.vehicle.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ButtonNote
import kr.autolog.vehicle.ui.design.components.CheckRow
import kr.autolog.vehicle.ui.design.components.PeriodSwitcher
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.ScrollColumn
import kr.autolog.vehicle.ui.design.components.Section
import kr.autolog.vehicle.ui.design.motion.ExpandSwap
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.design.twoColumns
import kr.autolog.vehicle.ui.mail.mailErrorText

@Composable
fun ReportRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { ReportViewModel(graph.reports, graph.trips, graph.settings, graph.navigator, graph.clock) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    // 화면에 (다시) 들어올 때마다 — 설정에서 메일 계정을 연결하고 돌아오면 [보내기]로 바뀐다
    LaunchedEffect(Unit) { vm.onAction(ReportAction.Resume) }
    ReportScreen(state, vm::onAction)
}

/**
 * 운행기록부 내보내기(스펙 7.7). 위에서 아래로: 머리 줄 → ◀ 달 ▶ → 요약 카드 → 받는 사람(체크) → 사내 서버 안내 한 줄.
 * 맨 아래 고정: 지금 단계 한 줄 + 주 버튼 하나(만들기 → 보내기 · N명 → 완료). 진입 후 2탭으로 발송(수용 기준).
 * 내용은 읽기 쉬운 폭(`sheetMaxWidth`)으로 가운데.
 */
@Composable
fun ReportScreen(state: ReportUiState, onAction: (ReportAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(screenMargin()),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        ScreenHeader(stringResource(R.string.report_title)) { onAction(ReportAction.Back) }
        Column(
            Modifier.weight(1f).widthIn(max = Size.sheetMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            ScrollColumn(Modifier.weight(1f).fillMaxWidth()) {
                // 이 화면은 달 이동이 한 줄에 혼자라 가운데(제목·요약 카드와 같은 세로축)
                PeriodSwitcher(
                    state.month,
                    state.canGoNext,
                    onPrev = { onAction(ReportAction.PrevMonth) },
                    onNext = { onAction(ReportAction.NextMonth) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                // 달이 바뀌면 요약이 그 자리에서 교체된다
                FadeThrough(state, contentKey = { it.month }, label = "reportSummary") { shown ->
                    shown.summary?.let { SummaryCard(it) }
                }
                Recipients(state, onAction)
                state.serverUpload?.let { ServerLine(it) }
            }
            StepMessage(state)
            PrimaryButton(
                primaryLabel(state),
                onClick = { onAction(ReportAction.Primary) },
                modifier = Modifier.fillMaxWidth(),
                enabled = primaryEnabled(state),
                loading = state.step == ReportStep.Building || state.step == ReportStep.Sending,
            )
        }
    }
}

/** 요약: 운행 · 총 거리 · 업무 비율 · 검토 필요. 넓은 창·분할 화면은 네 칸 한 줄, 세로로 긴 창은 두 칸씩 두 줄. */
@Composable
private fun SummaryCard(summary: ReportSummaryUi) {
    val facts = listOf(
        stringResource(R.string.report_trips, summary.tripCount) to stringResource(R.string.report_trips_label),
        stringResource(R.string.trip_km, summary.totalKm) to stringResource(R.string.report_total_label),
        (
            summary.businessPercent?.let { stringResource(R.string.report_percent, it) }
                ?: stringResource(R.string.value_empty)
            ) to
            stringResource(R.string.report_business_label),
        stringResource(R.string.report_trips, summary.reviewCount) to stringResource(R.string.report_review_label),
    )
    val rows = if (LocalWindowLayout.current.twoColumns) listOf(facts) else facts.chunked(2)
    Column(
        Modifier
            .padding(top = Space.s4)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(AutoLogTheme.colors.surface1)
            .padding(Space.s5),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        rows.forEach { row -> Row(Modifier.fillMaxWidth()) { row.forEach { (value, label) -> Fact(value, label) } } }
    }
}

@Composable
private fun RowScope.Fact(value: String, label: String) {
    Column(Modifier.weight(1f)) {
        Text(value, style = AutoLogTheme.type.title, color = AutoLogTheme.colors.textPrimary)
        Text(label, style = AutoLogTheme.type.caption, color = AutoLogTheme.colors.textSecondary)
    }
}

/** 받는 사람 체크 목록(서버 배포분 먼저, 처음엔 모두 고름). 만드는 중·보내는 중·보낸 뒤엔 바꿀 수 없다. */
@Composable
private fun Recipients(state: ReportUiState, onAction: (ReportAction) -> Unit) {
    val editable = state.step !is ReportStep.Sent && state.step != ReportStep.Sending
    Section(stringResource(R.string.report_recipients), Modifier.padding(top = Space.s6)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            state.recipients.forEach { r ->
                CheckRow(
                    text = r.name ?: r.email,
                    checked = r.selected,
                    onClick = if (editable) ({ onAction(ReportAction.ToggleRecipient(r.email)) }) else null,
                    detail = if (r.fromServer) stringResource(R.string.report_recipient_server, r.email) else r.email,
                )
            }
        }
    }
}

/** "사내 서버로도 자동 전송돼요 · 마지막 전송 10:24"(스펙 8장). */
@Composable
private fun ServerLine(upload: ServerUploadUi) {
    Text(
        upload.lastUploadTime?.let { stringResource(R.string.report_server_last, it) }
            ?: stringResource(R.string.report_server),
        modifier = Modifier.padding(top = Space.s4),
        style = AutoLogTheme.type.caption,
        color = AutoLogTheme.colors.textSecondary,
    )
}

/** 지금 단계 한 줄(버튼 바로 위). 할 말이 없으면 그리지 않는다. */
@Composable
private fun StepMessage(state: ReportUiState) {
    // 단계가 바뀌면 한 줄이 펼쳐지거나 그 자리에서 바뀌거나 접힌다
    ExpandSwap(stepMessage(state), Modifier.fillMaxWidth()) { (text, color) -> ButtonNote(text, color = color) }
}

@Composable
private fun stepMessage(state: ReportUiState): Pair<String, Color>? {
    val c = AutoLogTheme.colors
    return when (val step = state.step) {
        ReportStep.Ready ->
            if (state.summary?.tripCount == 0) stringResource(R.string.report_no_trips) to c.textTertiary else null
        is ReportStep.BuildFailed -> step.reason to c.danger
        // 만든 뒤엔 버튼([보내기 · N명])이 다음 할 일을 말한다 — 메일 계정이 없을 때만 이유 한 줄
        ReportStep.Built -> if (state.smtpConfigured) {
            null
        } else {
            stringResource(R.string.mail_not_configured) to
                c.warning
        }
        is ReportStep.Sent -> stringResource(R.string.report_sent, step.count) to c.success
        is ReportStep.SendFailed -> stringResource(mailErrorText(step.error)) to c.danger
        ReportStep.Building, ReportStep.Sending -> null
    }
}

@Composable
private fun primaryLabel(state: ReportUiState): String = when (val step = state.step) {
    ReportStep.Ready, ReportStep.Building -> stringResource(R.string.report_build)
    is ReportStep.BuildFailed -> stringResource(R.string.report_build_again)
    ReportStep.Built, ReportStep.Sending, is ReportStep.SendFailed -> when {
        !state.smtpConfigured -> stringResource(R.string.report_connect_mail)
        step is ReportStep.SendFailed -> stringResource(R.string.report_send_again)
        else -> stringResource(R.string.report_send, state.selectedCount)
    }
    is ReportStep.Sent -> stringResource(R.string.action_done)
}

private fun primaryEnabled(state: ReportUiState): Boolean = when (state.step) {
    ReportStep.Ready, is ReportStep.BuildFailed -> (state.summary?.tripCount ?: 0) > 0
    ReportStep.Built, is ReportStep.SendFailed -> !state.smtpConfigured || state.selectedCount > 0
    else -> true
}
