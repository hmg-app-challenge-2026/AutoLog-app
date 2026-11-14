package kr.autolog.vehicle.ui.result

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.R
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.data.usage.UsageTier
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.nav.TripResultMode
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.ActionBar
import kr.autolog.vehicle.ui.design.components.BarAction
import kr.autolog.vehicle.ui.design.components.ButtonNote
import kr.autolog.vehicle.ui.design.components.ChoiceRow
import kr.autolog.vehicle.ui.design.components.EdgeCountdownBar
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.RouteFlow
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.ScrollColumn
import kr.autolog.vehicle.ui.design.components.Section
import kr.autolog.vehicle.ui.design.components.TertiaryButton
import kr.autolog.vehicle.ui.design.components.TopLine
import kr.autolog.vehicle.ui.design.durationText
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.design.twoColumns
import kr.autolog.vehicle.ui.trips.tripWhenText

@Composable
fun TripResultRoute(tripId: String, mode: TripResultMode = TripResultMode.AFTER_TRIP) {
    val graph = LocalAppGraph.current
    val vm =
        viewModel {
            TripResultViewModel(
                tripId,
                graph.tripResults,
                graph.session,
                graph.navigator,
                graph.clock,
                mode,
                parked = graph.usage.tier.map { it == UsageTier.FULL },
            )
        }
    val state by vm.uiState.collectAsStateWithLifecycle()
    TripResultScreen(state, vm::onAction)
}

/**
 * 스펙 7.3 운행 결과(D-27). **정렬축은 하나**: 화면 여백 안의 콘텐츠 폭을 좌우 반반으로 나누고, 아래 행동 줄도 같은 두 칸을 쓴다.
 * - 맨 위 줄 가운데: 제목 "운행 기록을 완료했어요"(운전자 배지 없음).
 * - 왼쪽 칸 = 운행 요약: "운행 거리 · 시간"(큰 거리 옆에 작은 시간) → "운행 경로"(○—● 경로선).
 * - 오른쪽 칸 = 운행 목적: 같은 폭 선택형 행 3개(8 dp 간격).
 * - 두 칸 아래 가운데 = "일반 업무용 거리 48.2 km로 분류돼요. 3초 후 자동 저장돼요."(선택의 결과 + 남은 시간), 버튼과 40 dp 위.
 * - 아래 행동 줄: [기록 이어가기](종료 대기 중에만) · [수정하기](보기 모드에서만) · [저장하기](주)를 같은 폭으로 가운데에.
 *   남은 시간은 분류 문구 뒤 한 문장 + 맨 위 가장자리 막대 — 막대가 다 사라지는 순간 [저장하기]가 저절로 눌린다.
 * - 정차·주행 중(D-35): 아래는 "3초 후 자동 저장돼요." + "주차하면 운행 기록을 수정할 수 있어요" 두 줄(버튼·분류 문구·막대 없음) — 누를 것 없이 5초 뒤 저절로 저장된다.
 * - 저장을 마치면 저장 완료 화면([TripSaved]: 체크 애니메이션 + "운행 기록을 저장했어요" + [확인])으로 바뀐다.
 * 넓은 창과 Pleos 1:1 분할 한 칸(≈1280×1268)은 같은 두 칸 배치·같은 글자. 세로로 긴 창(TALL)만 세로 한 칸(넘치면 그 칸만 스크롤) + 분류 문구 + 버튼 두 줄.
 */
@Composable
fun TripResultScreen(state: TripResultUiState, onAction: (TripResultAction) -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalWindowLayout.current
    // 저장을 마치면 결과 카드가 사라지고 저장 완료 화면(TripSaved)이 같은 자리에서 떠오른다(디자인 언어 8.2 같은 자리 교체)
    FadeThrough(state, modifier.fillMaxSize(), contentKey = { it.saved }, label = "saved") { shown ->
        if (shown.saved) {
            TripSaved(onAction)
        } else {
            Box(Modifier.fillMaxSize()) {
                ResultBody(shown, onAction, layout)
                // 맨 위 가장자리에 붙는 남은 시간 막대 — 오른쪽 끝이 왼쪽으로 줄어든다(수정 중엔 없다). 정차·주행 중엔 없다(D-35)
                shown.autoCloseRemainingMs?.takeUnless { LocalDrivingLocked.current }?.let {
                    EdgeCountdownBar(it, shown.autoCloseTotalMs, Modifier.align(Alignment.TopStart))
                }
                ModalHost(visible = shown.confirmingDelete) {
                    DeleteTripDialog(
                        onConfirm = { onAction(TripResultAction.ConfirmDelete) },
                        onCancel = { onAction(TripResultAction.CancelDelete) },
                    )
                }
                // 거리 고치기(F-05) — 닫히는 동안에도 그릴 값이 있게 빈 편집기로 대신한다
                ModalHost(visible = shown.distanceEditor != null) {
                    DistanceEditDialog(shown.distanceEditor ?: DistanceEditor(), onAction)
                }
            }
        }
    }
}

@Composable
private fun ResultBody(state: TripResultUiState, onAction: (TripResultAction) -> Unit, layout: WindowLayout) {
    val c = AutoLogTheme.colors
    val twoColumns = layout.twoColumns
    val margin = screenMargin()
    // 두 칸 배치(넓은 창·분할 화면)는 위아래 여백을 16 dp로 줄여 두 칸에 높이를 준다(에뮬레이터에서 맞춤, PR #4)
    val vertical = if (twoColumns) Space.s4 else margin
    Column(
        Modifier.fillMaxSize().padding(start = margin, top = vertical, end = margin, bottom = vertical),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        if (state.review) {
            // 사후 확인은 운행 기록 조회에서 들어온 보조 화면 — 다른 보조 화면처럼 왼쪽 위 <(저장하지 않고 목록으로, D-39)
            ScreenHeader(resultTitle(state)) { onAction(TripResultAction.Close) }
        } else {
            TopLine {
                Text(
                    resultTitle(state),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = AutoLogTheme.windowType.resultTitle,
                    color = c.textPrimary,
                )
            }
        }
        when {
            state.loading -> Unit
            state.notFound -> {
                ButtonNote(stringResource(R.string.result_not_found))
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    stringResource(R.string.result_home),
                    onClick = { onAction(TripResultAction.Close) },
                    modifier = Modifier.fillMaxWidth(),
                    height = Size.buttonMd,
                )
            }
            twoColumns -> {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    val availableHeight = maxHeight
                    val spacious = availableHeight >= Size.spaciousHeight
                    val summaryGap = if (spacious) SUMMARY_GAP_SPACIOUS else Space.s7
                    TwoColumns(
                        Modifier
                            .heightIn(max = availableHeight)
                            .then(if (spacious) Modifier.offset(y = -COLUMNS_LIFT_SPACIOUS) else Modifier),
                    ) {
                        ScrollColumn(Modifier.weight(1f).heightIn(max = availableHeight)) {
                            TripSummary(state, onAction, gap = summaryGap)
                        }
                        ScrollColumn(Modifier.weight(1f).heightIn(max = availableHeight)) {
                            Purpose(state, onAction)
                        }
                    }
                }
                // 분류 문구는 버튼에서 40 dp(줄 간격 8 + 32) 띄워 위로 — 버튼 글자와 붙어 한 덩어리로 읽히지 않게(2026-11-10 사용자 지시)
                ClassificationSummary(state, Modifier.padding(bottom = CLASSIFICATION_GAP))
                // 행동 줄: 같은 폭 버튼을 가운데에 모은다(분류 문구와 같은 가운데 축). 남은 시간은 주 버튼 안에
                // 수정에 들어가거나 나오면 버튼 줄이 그 자리에서 바뀐다(버튼 개수·강조가 달라진다).
                // 교체 상자는 폭을 채우지 않는다 — 채우면 버튼 줄의 최대 폭(480 dp × n)이 무시된다
                if (LocalDrivingLocked.current) {
                    DrivingNote()
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FadeThrough(state, contentAlignment = Alignment.Center, contentKey = {
                            resultButtons(it)
                        }, label = "actions") {
                            ActionBar(resultActions(it, onAction))
                        }
                    }
                }
            }
            else -> {
                // 세로로 긴 창: 넓은 창과 같은 순서 — 운행 거리 · 시간 → 운행 경로 → 운행 목적(세로 선택형 행).
                // 다 들어가지 않으면 이 칸만 스크롤되고 분류 문구·버튼은 늘 보인다
                val scroll = rememberScrollState()
                // [수정]을 누르면 아래에 있는 운행 목적이 보이게 끝까지 내린다
                LaunchedEffect(state.editing) { if (state.editing) scroll.animateScrollTo(scroll.maxValue) }
                ScrollColumn(Modifier.weight(1f).fillMaxWidth(), scroll) {
                    TripSummary(state, onAction)
                    Spacer(Modifier.height(Space.s6))
                    Purpose(state, onAction)
                }
                ClassificationSummary(state)
                if (LocalDrivingLocked.current) {
                    DrivingNote()
                } else {
                    FadeThrough(state, Modifier.fillMaxWidth(), contentKey = { resultButtons(it) }, label = "actions") {
                        ActionBar(resultActions(it, onAction))
                    }
                }
            }
        }
    }
}

/**
 * 정차·주행 중 버튼 줄 자리(D-35): 버튼을 모두 빼고 같은 높이 칸 가운데에 안내 한 줄 — 위 두 칸이 주차 때와 같은 자리에 있게.
 * 고칠 곳은 주차 뒤 운행 기록 조회(D-26).
 */
@Composable
private fun DrivingNote() {
    Box(Modifier.fillMaxWidth().height(Size.buttonMd), contentAlignment = Alignment.Center) {
        ButtonNote(stringResource(R.string.result_driving_note))
    }
}

/** 콘텐츠 폭을 좌우 반반으로 — 위 섹션과 아래 행동 줄이 같은 두 칸을 쓴다(공통 그리드). */
@Composable
private fun TwoColumns(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s7), content = content)
}

/** 칸 높이가 넉넉한 차량 화면에서 거리·시간과 경로 사이 간격(112 dp). 작은 창은 48 dp. */
private val SUMMARY_GAP_SPACIOUS = Space.s7 * 2 + Space.s4

/** 두 칸 배치에서 분류 문구 아래에 더하는 간격 — 버튼과 40 dp(열 간격 8 + 32). */
private val CLASSIFICATION_GAP = Space.s6

/** 칸 높이가 넉넉한 차량 화면에서 두 칸을 세로 가운데보다 이만큼 위에 둔다. */
private val COLUMNS_LIFT_SPACIOUS = Space.s6

/**
 * 운행 결과의 섹션 하나. 두 칸 배치는 소제목 아래를 16 dp 더 띄운다(Pleos 에뮬레이터 2560×1440에서 맞춤, PR #4) —
 * 운행 요약과 운행 목적이 같은 규칙을 쓰도록 여기 한 곳에서.
 */
@Composable
private fun ResultSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    val twoColumns = LocalWindowLayout.current.twoColumns
    Section(stringResource(title)) {
        if (twoColumns) Spacer(Modifier.height(Space.s4))
        content()
    }
}

/** 운행 요약: "운행 거리 · 시간" → [gap] → "운행 경로", 위아래로. 수정 모드면 거리 아래 [거리 수정하기](F-05). */
@Composable
private fun TripSummary(state: TripResultUiState, onAction: (TripResultAction) -> Unit, gap: Dp = Space.s7) {
    Column {
        ResultSection(R.string.section_distance_time) {
            DistanceTime(state)
            if (state.editing) {
                TertiaryButton(
                    stringResource(R.string.result_edit_distance),
                    onClick = { onAction(TripResultAction.EditDistance) },
                )
            }
        }
        Spacer(Modifier.height(gap))
        ResultSection(R.string.section_route) { Route(state) }
    }
}

/**
 * 큰 거리 + 작은 시간(회색 — 보조 지표)을 같은 기준선에 놓는다(`resultDistance` · `resultTime`).
 */
@Composable
private fun DistanceTime(state: TripResultUiState) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val km = state.km?.let { stringResource(R.string.trip_km, it) } ?: stringResource(R.string.trip_km_unknown)
    val kmColor = if (state.km != null) c.textPrimary else c.textSecondary
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
        Text(km, modifier = Modifier.alignByBaseline(), style = t.resultDistance, color = kmColor)
        state.minutes?.let {
            Text(minutesText(it), modifier = Modifier.alignByBaseline(), style = t.resultTime, color = c.textSecondary)
        }
    }
}

/**
 * 경로선 — 끝난 운행이라 ○—● 가는 선(흐름 모션 없음). 장소마다 이름 + 주소 2줄(I-05), 이름이 없으면 주소가 이름 자리에.
 * 둘 다 모르면 "위치 미상".
 */
@Composable
private fun Route(state: TripResultUiState) {
    val c = AutoLogTheme.colors
    val destination = state.destination ?: state.destinationAddress
    RouteFlow(
        origin = state.origin ?: state.originAddress,
        destination = destination ?: stringResource(R.string.place_unknown),
        destinationColor = if (destination != null) c.textPrimary else c.textSecondary,
        live = false,
        originDetail = state.originAddress.takeIf { state.origin != null },
        destinationDetail = state.destinationAddress.takeIf { state.destination != null },
    )
}

/** 운행 목적 — 같은 폭 선택형 행 3개(8 dp 간격, 보기 모드에선 누를 수 없음). */
@Composable
private fun Purpose(state: TripResultUiState, onAction: (TripResultAction) -> Unit) {
    ResultSection(R.string.section_purpose) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            PURPOSE_CHOICES.forEach { category ->
                ChoiceRow(
                    stringResource(choiceLabel(category)),
                    selected = category == state.selected,
                    onClick = if (state.editing) ({ onAction(TripResultAction.Select(category)) }) else null,
                )
            }
        }
    }
}

/** 선택한 운행 목적의 분류 결과를 하단 행동 버튼 바로 위, 화면 가운데에 표시한다. */
@Composable
private fun ClassificationSummary(state: TripResultUiState, modifier: Modifier = Modifier) {
    // 목적을 바꾸거나 수정에 들어가 "n초 후 자동 저장돼요."가 빠지면 문구가 그 자리에서 바뀐다. 초가 줄어드는 건 교체가 아니다
    FadeThrough(
        state,
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
        contentKey = { Triple(it.selected, it.km, it.autoCloseSec != null) },
        label = "classification",
    ) { ClassificationText(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassificationText(state: TripResultUiState) {
    val c = AutoLogTheme.colors
    val style = AutoLogTheme.windowType.resultClassification
    val kind = stringResource(classLabel(state.selected))
    // 정차·주행 중엔 분류 문구를 빼고 남은 시간만 — 아래 문구를 줄인다(D-35, 2026-11-12 사용자 지시 "설명 문구가 너무 많다")
    val driving = LocalDrivingLocked.current
    // 분류 결과 + (남은 시간이 돌 때) "3초 후 자동 저장돼요" — 넓은 화면은 한 줄, 폭이 모자라면 문장 단위로 다음 줄에
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s3, Alignment.CenterHorizontally),
    ) {
        if (!driving) {
            Text(
                state.km?.let { stringResource(R.string.result_classified, kind, stringResource(R.string.trip_km, it)) }
                    ?: stringResource(R.string.result_classified_no_km, kind),
                style = style,
                color = c.textPrimary,
            )
        }
        state.autoCloseSec?.let {
            Text(stringResource(R.string.result_auto_save, durationText(it)), style = style, color = c.textSecondary)
        }
    }
}

/** 행동 버튼 하나 — 두 칸 배치·세로 창이 같은 목록([resultButtons])을 배치만 달리 그린다. [danger] = 빨간 [삭제하기](D-39). */
private data class ResultButton(
    @StringRes val label: Int,
    val action: TripResultAction,
    val primary: Boolean,
    val danger: Boolean = false,
)

/**
 * 지금 보여 줄 행동 버튼(이 순서). 운행 직후: [기록 이어가기](종료 대기 중에만) · [수정하기] · [저장하기](주 —
 * 남은 시간이 다 되면 저절로 눌리는 버튼). 수정 모드는 [수정하기] 자리에 빨간 [삭제하기].
 * 사후 확인(D-29·D-39): 보기 [수정하기] · [삭제하기], 수정 [저장하기] · [삭제하기](주) — 닫기는 왼쪽 위 <.
 */
private fun resultButtons(state: TripResultUiState): List<ResultButton> = buildList {
    val save = ResultButton(R.string.result_save, TripResultAction.Save, primary = true)
    val edit = ResultButton(R.string.result_edit, TripResultAction.Edit, primary = false)
    // 빨간 [삭제하기] — 누르면 확인 팝업(D-39, 2026-11-13 사용자 결정)
    val delete = ResultButton(R.string.result_delete, TripResultAction.Delete, primary = false, danger = true)
    when {
        // 사후 확인(D-29·D-39): 보기 [수정하기] · [삭제하기], 수정 [저장하기] · [삭제하기]. 닫기는 왼쪽 위 <
        // [삭제하기]는 보기·수정 모두 오른쪽 같은 자리 — 수정에 들어가도 움직이지 않게(2026-11-13 사용자 결정)
        state.review -> if (state.editing) addAll(listOf(save, delete)) else addAll(listOf(edit, delete))
        // 운행 직후 수정: [기록 이어가기](종료 대기 중에만) · [삭제하기] · [저장하기]
        state.editing -> {
            if (state.canContinue) {
                add(
                    ResultButton(R.string.result_continue, TripResultAction.Continue, primary = false),
                )
            }
            addAll(listOf(delete, save))
        }
        // 운행 직후 보기: [기록 이어가기](종료 대기 중에만) · [수정하기] · [저장하기]
        else -> {
            if (state.canContinue) {
                add(
                    ResultButton(R.string.result_continue, TripResultAction.Continue, primary = false),
                )
            }
            addAll(listOf(edit, save))
        }
    }
}

/** 행동 줄([ActionBar]) — 주 버튼은 저장 중이면 진행 표시. 배치(두 칸·세로 창)는 ActionBar가 정한다. */
@Composable
private fun resultActions(state: TripResultUiState, onAction: (TripResultAction) -> Unit): List<BarAction> =
    resultButtons(state).map { button ->
        BarAction(
            stringResource(button.label),
            onClick = { onAction(button.action) },
            primary = button.primary,
            loading = button.primary && state.saving,
            danger = button.danger,
        )
    }

/** "운행 기록을 완료했어요", 사후 확인이면 "11월 7일 19:34 운행"(D-29). */
@Composable
private fun resultTitle(state: TripResultUiState): String =
    state.startedAt?.let { stringResource(R.string.result_review_title, tripWhenText(it)) }
        ?: stringResource(R.string.result_title)

@Composable
private fun minutesText(minutes: Int): String = if (minutes < MINUTES_PER_HOUR) {
    stringResource(R.string.trip_minutes, minutes)
} else {
    stringResource(R.string.trip_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60

/** 선택지 이름(일반 업무 · 출퇴근 · 개인 용무). */
@StringRes
private fun choiceLabel(category: PurposeCategory): Int = when (category) {
    PurposeCategory.COMMUTE -> R.string.purpose_commute
    PurposeCategory.NON_BUSINESS -> R.string.purpose_personal
    PurposeCategory.BUSINESS, PurposeCategory.UNDETERMINED -> R.string.purpose_business
}

/** 분류 이름(운행기록부 기준: 일반 업무용 · 출퇴근용 · 비업무용). */
@StringRes
private fun classLabel(category: PurposeCategory): Int = when (category) {
    PurposeCategory.COMMUTE -> R.string.purpose_class_commute
    PurposeCategory.NON_BUSINESS -> R.string.purpose_class_personal
    PurposeCategory.BUSINESS, PurposeCategory.UNDETERMINED -> R.string.purpose_class_business
}
