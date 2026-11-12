package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.ScrollColumn
import kr.autolog.vehicle.ui.design.components.SectionHeader
import kr.autolog.vehicle.ui.design.components.StatusLine
import kr.autolog.vehicle.ui.design.components.TopLine
import kr.autolog.vehicle.ui.design.durationText
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.screenMargin

@Composable
fun HomeRoute() {
    val graph = LocalAppGraph.current
    val vm =
        viewModel {
            HomeViewModel(
                graph.session,
                graph.context,
                graph.trips,
                graph.navigator,
                graph.clock,
            )
        }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, vm::onAction)
}

/**
 * 스펙 7.2 (D-23·D-24·D-25·D-26). 위에서 아래로:
 * 상태 줄(`● 기록 중 · 35분 경과`, 오른쪽은 운전자 배지) → 가운데 두 섹션(값 · 맥락) → 같은 모양 타일 4개.
 * 앱 안 알림은 없다(D-26) — 알려야 할 일은 시스템 알림(앱 A)이 맡고, 고칠 일은 사용자가 운행 기록 조회에서 고친다.
 * 예외는 결정이 꼭 필요한 팝업 둘뿐: 복구([RecoveryDialog])와 놓친 운행([MissedTripDialog]).
 */
@Composable
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalWindowLayout.current
    // 상태 줄 아래 소제목만 내려 배치하고, 값·목록·하단 동작의 레이아웃 높이는 유지한다.
    // 넓은 화면은 내용이 세로 가운데라 소제목만 96 dp 내려 상태 줄과 떼어 둔다(2026-11-10 사용자 지시)
    val sectionTitleOffset = Modifier.offset(y = if (layout == WindowLayout.WIDE) Space.s9 else Space.s3)
    Box(modifier.fillMaxSize()) {
        if (layout == WindowLayout.WIDE) {
            // 높이 640 dp 안에 상태 줄·두 섹션·타일이 다 들어가게 단 사이 간격은 16 dp
            Column(
                Modifier.fillMaxSize().padding(screenMargin()),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
            ) {
                TopLine { HomeStatus(state) }
                // 좌우 반반. 소제목은 위에 나란히 고정하고, 내용은 그 아래 남는 높이의 세로 가운데에 —
                // 짧은 내용(기록 중 거리·경로)이 소제목 바로 밑에 붙어 위로 쏠리지 않게. 길면(최근 운행 3건) 칸을 다 쓰고 그 칸만 스크롤
                // 대기 ↔ 기록 중이 바뀌면 두 섹션이 그 자리에서 교체된다(디자인 언어 8.2 — 나가는 쪽은 자기 상태로 그려진다)
                FadeThrough(state, Modifier.weight(1f), contentKey = { it.phase }, label = "homeSections") { shown ->
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.s7)) {
                        homeSections(shown, onAction).forEach { section ->
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                SectionHeader(section.title, sectionTitleOffset)
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    ScrollColumn(Modifier.fillMaxWidth(), content = section.content)
                                }
                            }
                        }
                    }
                }
                HomeDock(state, onAction)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(screenMargin()),
                verticalArrangement = Arrangement.spacedBy(Space.s5),
            ) {
                TopLine { HomeStatus(state) }
                // 가운데만 남는 높이를 쓴다 — 넘치면 이 영역이 스크롤되고 타일은 늘 보인다
                // 분할 화면 한 칸은 높이가 넉넉해 두 섹션을 상태 줄에서 48 dp 내리고 섹션 사이를 64 dp로(2026-11-10 사용자 지시).
                // 세로 창은 그대로(바로 아래 32 dp)
                val narrow = layout == WindowLayout.NARROW
                FadeThrough(state, Modifier.weight(1f).fillMaxWidth(), contentKey = {
                    it.phase
                }, label = "homeSections") { shown ->
                    ScrollColumn(Modifier.fillMaxSize()) {
                        if (narrow) Spacer(Modifier.height(Space.s7))
                        // 한 칸에 위아래로: 값 → 맥락
                        homeSections(shown, onAction).forEachIndexed { i, section ->
                            if (i > 0) Spacer(Modifier.height(if (narrow) Space.s8 else Space.s6))
                            Column {
                                SectionHeader(section.title, sectionTitleOffset)
                                section.content(this)
                            }
                        }
                    }
                }
                HomeDock(state, onAction)
            }
        }
        // 결정이 필요한 팝업은 주차 중에만(정차·주행 중엔 같은 Home을 읽기 전용으로 — D-31). 주차하면 그때 묻는다
        val parked = !LocalDrivingLocked.current
        ModalHost(visible = parked && state.phase == HomePhase.NEEDS_RECOVERY) {
            RecoveryDialog(state.recoveryExtraKm) { onAction(HomeAction.Recover(it)) }
        }
        ModalHost(state.missedKm?.takeIf { parked && state.phase == HomePhase.READY }) { missedKm ->
            MissedTripDialog(
                missedKm,
                onAdd = { onAction(HomeAction.AcceptMissedTrip) },
                onIgnore = { onAction(HomeAction.IgnoreMissedTrip) },
            )
        }
    }
}

/**
 * 상태 줄: `● 대기 중` / `● 기록 중 · 35분 경과` / 종료 대기면 `● 기록 중 · 2분 41초 후 기록 종료`.
 * 점 색이 상태를 말한다(기록 중 초록, 대기 흐린 회색).
 */
@Composable
private fun HomeStatus(state: HomeUiState) {
    val c = AutoLogTheme.colors
    val recording = state.phase == HomePhase.RECORDING_PARKED
    if (recording) {
        StatusLine(
            text = stringResource(R.string.status_recording),
            dot = c.success,
            detail = state.autoEndInSec?.let { stringResource(R.string.home_status_auto_end, durationText(it)) }
                ?: stringResource(R.string.home_status_elapsed, state.elapsedMin ?: 0),
        )
    } else {
        StatusLine(text = stringResource(R.string.status_ready), dot = c.textTertiary)
    }
}
