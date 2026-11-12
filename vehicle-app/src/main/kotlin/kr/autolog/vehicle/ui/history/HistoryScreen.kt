package kr.autolog.vehicle.ui.history

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.Avatar
import kr.autolog.vehicle.ui.design.components.PeriodSwitcher
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.components.TripListRow
import kr.autolog.vehicle.ui.design.components.tappable
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.trips.TripRowUi
import kr.autolog.vehicle.ui.trips.tripMetaText
import kr.autolog.vehicle.ui.trips.tripRouteText

@Composable
fun HistoryRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { HistoryViewModel(graph.trips, graph.profile, graph.session, graph.navigator, graph.clock) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, vm::onAction)
}

/**
 * 운행 기록 조회(스펙 7.8, F-12). 위에서 아래로: 머리 줄(X · "운행 기록") → 달 이동 · 합계
 * → 운행 목록(최신 순, 넘치면 스크롤 — 자동 스크롤 없음) → 아래 가운데 [운행기록부 내보내기].
 * 줄을 누르면 운행 결과 카드(사후 확인 — 카운트다운 없음, D-29). 운전자 미확정 줄은 그 자리에서 후보 1탭으로 지정(F-20).
 */
@Composable
fun HistoryScreen(state: HistoryUiState, onAction: (HistoryAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(screenMargin()),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        ScreenHeader(stringResource(R.string.history_title)) { onAction(HistoryAction.Back) }
        Column(
            Modifier.weight(1f).widthIn(max = Size.sheetMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Toolbar(state, onAction)
            // 달이 바뀌면 목록이 그 자리에서 교체된다(같은 달 안의 변화 — 운전자 지정 등 — 는 줄 단위로 움직인다)
            FadeThrough(state, Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center, contentKey = {
                it.month to it.loading
            }, label = "historyMonth") { shown ->
                when {
                    shown.loading -> Unit
                    shown.rows.isEmpty() ->
                        Text(
                            stringResource(R.string.history_empty),
                            modifier = Modifier.fillMaxWidth(),
                            style = AutoLogTheme.type.body,
                            color = AutoLogTheme.colors.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    else -> TripList(shown, onAction, Modifier.fillMaxSize())
                }
            }
            SecondaryButton(
                stringResource(R.string.history_report),
                onClick = { onAction(HistoryAction.OpenReport) },
                modifier = Modifier.widthIn(
                    max = Size.listActionMaxWidth,
                ).fillMaxWidth().align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * 달 이동(◀ 2026년 11월 ▶)과 그 아래 합계("23회 운행 · 412.7 km")를 가운데에 둔다.
 */
@Composable
private fun Toolbar(state: HistoryUiState, onAction: (HistoryAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MonthSwitcher(state, onAction)
        Summary(state)
    }
}

@Composable
private fun MonthSwitcher(state: HistoryUiState, onAction: (HistoryAction) -> Unit) = PeriodSwitcher(
    state.month,
    state.canGoNext,
    onPrev = { onAction(HistoryAction.PrevMonth) },
    onNext = { onAction(HistoryAction.NextMonth) },
)

@Composable
private fun Summary(state: HistoryUiState) {
    FadeThrough(
        stringResource(R.string.history_summary, state.tripCount, state.totalKm),
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
        label = "historySummary",
    ) {
        Text(
            it,
            style = AutoLogTheme.type.body,
            color = AutoLogTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TripList(state: HistoryUiState, onAction: (HistoryAction) -> Unit, modifier: Modifier) {
    val motion = AutoLogTheme.motion
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        items(state.rows, key = { it.tripId }) { row ->
            TripListRow(
                modifier = Modifier.animateItem(
                    fadeInSpec = motion.quick(),
                    placementSpec = motion.smooth(IntOffset.VisibilityThreshold),
                    fadeOutSpec = motion.swapOut(),
                ),
                title = tripRouteText(row),
                subtitle = tripMetaText(row, withDriver = true, withUpload = true),
                onClick = { onAction(HistoryAction.Open(row.tripId)) },
                extra =
                if (row.driverUnset && state.driverChoices.isNotEmpty()) {
                    { DriverPicker(row, state.driverChoices, onAction) }
                } else {
                    null
                },
            )
        }
    }
}

/** 운전자 미확정 줄 안: "운전자 지정" + 후보(아바타 + 이름) 1탭(F-20, D-19). 좁으면 다음 줄로 넘긴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverPicker(row: TripRowUi, choices: List<DriverChoice>, onAction: (HistoryAction) -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    FlowRow(
        Modifier.padding(top = Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.history_assign_driver),
            modifier = Modifier.align(Alignment.CenterVertically),
            style = t.caption,
            color = c.textSecondary,
        )
        choices.forEach { choice ->
            Row(
                Modifier
                    .heightIn(min = Size.touchMin)
                    .tappable({ onAction(HistoryAction.AssignDriver(row.tripId, choice.userId)) })
                    .padding(horizontal = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                Avatar(choice.userId, choice.name, Size.avatarChip)
                Text(choice.name, style = t.label, color = c.textPrimary)
            }
        }
    }
}
