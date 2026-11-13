package kr.autolog.vehicle.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ActionBar
import kr.autolog.vehicle.ui.design.components.BarAction
import kr.autolog.vehicle.ui.design.components.ButtonNote
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.ScrollColumn
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.design.twoColumns

/** 사진을 크게 볼 때 팝업 안 사진 높이(화면 높이 대비). */
private const val EXPANDED_IMAGE_HEIGHT = 0.75f

@Composable
fun FuelEntryRoute(receiptId: String?) {
    val graph = LocalAppGraph.current
    val vm =
        viewModel {
            FuelEntryViewModel(receiptId, graph.receipts, graph.session, graph.trips, graph.clock, graph.navigator)
        }
    val state by vm.uiState.collectAsStateWithLifecycle()
    FuelEntryScreen(state, vm::onAction)
}

/**
 * 주유 기록 입력 창(스펙 7.5, D-20·D-38). 운행 결과 카드와 같은 문법이다:
 * - 두 칸 배치(넓은 창·분할 화면 한 칸): 왼쪽 "주유 내역" · 오른쪽 "연결할 운행" — 사람이 정할 것(연결할 운행)이 첫 화면에 보인다.
 * - 왼쪽은 영수증이면 **요약**(금액이 주인공 + 보조 두 줄 + 작은 사진), [수정하기]를 누르면 **칸 목록**(봐야 할 칸이 먼저).
 *   직접 입력·금액을 못 읽은 영수증은 처음부터 칸 목록.
 * - 아래 가운데: 상태 한 줄(무엇을 해야 [추가하기]가 되는지 / 다 됐는지) → 행동 줄([수정하기] · [추가하기]).
 * 세로로 긴 창은 같은 순서로 한 칸에 쌓는다. 행동 줄은 스크롤 밖 하단 고정.
 */
@Composable
fun FuelEntryScreen(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit, modifier: Modifier = Modifier) {
    var imageExpanded by remember { mutableStateOf(false) }
    val expandImage = { imageExpanded = true }
    BoxWithConstraints(modifier.fillMaxSize().padding(screenMargin())) {
        val availableHeight = maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            ScreenHeader(stringResource(R.string.fuel_entry_title)) { onAction(FuelEntryAction.Back) }
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.fuel_loading),
                        style = AutoLogTheme.type.body,
                        color = AutoLogTheme.colors.textSecondary,
                    )
                }
                // 두 칸은 남는 높이의 세로 가운데에(운행 결과와 같은 배치) — 짧은 요약이 위로 쏠려 아래가 비지 않게.
                // 칸 목록처럼 길면 높이를 다 쓰고 칸마다 스크롤
                LocalWindowLayout.current.twoColumns ->
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        val columnHeight = maxHeight
                        Row(
                            Modifier.fillMaxWidth().heightIn(max = columnHeight),
                            horizontalArrangement = Arrangement.spacedBy(Space.s7),
                        ) {
                            ScrollColumn(Modifier.weight(1f).heightIn(max = columnHeight)) {
                                FuelDetails(state, onAction, expandImage)
                            }
                            ScrollColumn(Modifier.weight(1f).heightIn(max = columnHeight)) {
                                FuelTripChoices(state, onAction)
                            }
                        }
                    }
                else -> ScrollColumn(Modifier.weight(1f).fillMaxWidth()) {
                    FuelDetails(state, onAction, expandImage)
                    Spacer(Modifier.height(Space.s6))
                    FuelTripChoices(state, onAction)
                }
            }
            if (!state.loading) FuelActions(state, onAction)
        }
        ModalHost(state.editing) { FieldEditor(it, state, onAction) }
        ModalHost(visible = imageExpanded) {
            ModalCard(title = stringResource(R.string.fuel_receipt_image), onClose = { imageExpanded = false }) {
                ReceiptImage(state, Modifier.fillMaxWidth().height(availableHeight * EXPANDED_IMAGE_HEIGHT)) {
                    imageExpanded = false
                }
            }
        }
    }
}

/**
 * 아래 가운데: 상태 한 줄(오류가 있으면 오류) → 행동 줄. 요약이면 [수정하기] · [추가하기], 칸 목록이면 [추가하기] 하나.
 * 상태 줄은 운행 결과의 분류 문구처럼 버튼에서 띄워 위로 — 버튼 글자와 한 덩어리로 읽히지 않게.
 */
@Composable
private fun FuelActions(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val error = state.error?.let { errorText(it) to AutoLogTheme.colors.danger }
        (error ?: state.notice?.let { noticeText(it) to noticeColor(it) })?.let { (text, color) ->
            ButtonNote(text, style = AutoLogTheme.windowType.resultClassification, color = color)
            Spacer(Modifier.height(Space.s5))
        }
        val add =
            BarAction(
                stringResource(R.string.fuel_add_confirm),
                onClick = { onAction(FuelEntryAction.Add) },
                primary = true,
                enabled = state.canAdd,
                loading = state.saving,
            )
        val actions =
            if (state.showsSummary) {
                listOf(
                    BarAction(stringResource(R.string.fuel_edit), onClick = {
                        onAction(FuelEntryAction.OpenDetails)
                    }),
                    add,
                )
            } else {
                listOf(add)
            }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ActionBar(actions) }
    }
}

@Composable
private fun noticeText(notice: FuelNotice): String = when (notice) {
    FuelNotice.ArithFailed -> stringResource(R.string.fuel_arith_failed)
    FuelNotice.OcrFailed -> stringResource(R.string.fuel_ocr_failed)
    FuelNotice.AmountMissing -> stringResource(R.string.fuel_notice_amount)
    // 어느 칸인지는 요약의 주황 글자·칸 목록의 "확인 필요"가 말한다 — 안내는 할 일 하나로(2026-11-13 사용자 지시)
    is FuelNotice.NeedsCheck -> stringResource(R.string.fuel_notice_check)
    FuelNotice.ArithOk -> stringResource(R.string.fuel_arith_ok)
}

/** 막는 것은 빨강(검산 실패), 봐야 할 것은 주황, 다 됐으면 초록, 할 일 안내는 회색. 글자가 내용을 말하므로 색만으로 전하지 않는다. */
@Composable
private fun noticeColor(notice: FuelNotice) = when (notice) {
    FuelNotice.ArithFailed -> AutoLogTheme.colors.danger
    is FuelNotice.NeedsCheck -> AutoLogTheme.colors.warning
    FuelNotice.ArithOk -> AutoLogTheme.colors.success
    FuelNotice.OcrFailed, FuelNotice.AmountMissing -> AutoLogTheme.colors.textSecondary
}

@Composable
private fun errorText(error: FuelEntryError): String = when (error) {
    FuelEntryError.ReceiptMissing -> stringResource(R.string.fuel_receipt_missing)
    is FuelEntryError.Rejected -> error.reason
}
