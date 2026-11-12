package kr.autolog.vehicle.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.ScrollColumn
import kr.autolog.vehicle.ui.design.components.tappable
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.design.twoColumns

/** 넓은 창에서 영수증 사진이 차지하는 높이(화면 높이 대비). */
private const val WIDE_IMAGE_HEIGHT = 0.55f

/** 좁은·세로 창에서 영수증 사진이 차지하는 높이(화면 높이 대비). */
private const val STACKED_IMAGE_HEIGHT = 1f / 3

/** 사진을 눌러 크게 볼 때 팝업 안 사진 높이(화면 높이 대비). */
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
 * 주유 기록 입력 창(스펙 7.5, D-20, 디자인 언어 7장 Media + Form). 영수증·직접 입력이 같은 칸과 [추가하기]를 쓴다.
 * 넓은 창 + 영수증이면 왼쪽 사진 / 오른쪽 칸, 그 밖에는 사진(있으면) 아래 칸을 한 줄로. [추가하기]는 스크롤 밖 하단 고정.
 */
@Composable
fun FuelEntryScreen(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit, modifier: Modifier = Modifier) {
    var imageExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().padding(screenMargin())) {
        val availableHeight = maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            ScreenHeader(stringResource(R.string.fuel_entry_title)) { onAction(FuelEntryAction.Back) }
            val twoColumns = LocalWindowLayout.current.twoColumns && state.receipt != null
            if (twoColumns) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s7)) {
                    ReceiptImage(state, Modifier.weight(1f).height(availableHeight * WIDE_IMAGE_HEIGHT)) {
                        imageExpanded = true
                    }
                    ScrollColumn(Modifier.weight(1f)) { FuelForm(state, onAction) }
                }
            } else {
                ScrollColumn(Modifier.weight(1f).fillMaxWidth()) {
                    // 넓은 화면에서 칸이 끝없이 늘어나지 않게 가운데 최대 폭
                    Column(
                        Modifier.widthIn(max = Size.sheetMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally),
                    ) {
                        if (state.receipt != null) {
                            ReceiptImage(
                                state,
                                Modifier.fillMaxWidth().height(availableHeight * STACKED_IMAGE_HEIGHT),
                            ) {
                                imageExpanded = true
                            }
                            Spacer(Modifier.height(Space.s5))
                        }
                        FuelForm(state, onAction)
                    }
                }
            }
            // 한 칸 배치면 [추가하기]도 칸과 같은 폭으로 가운데(넓은 화면에서 버튼만 화면 끝까지 퍼지지 않게)
            PrimaryButton(
                stringResource(R.string.fuel_add_confirm),
                onClick = { onAction(FuelEntryAction.Add) },
                modifier =
                if (twoColumns) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.widthIn(max = Size.sheetMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally)
                },
                enabled = state.canAdd,
                loading = state.saving,
            )
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

/** 영수증 사진(누르면 크게). 불러오는 중·실패는 사진 자리에 문구. */
@Composable
private fun ReceiptImage(state: FuelEntryUiState, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.tappable(
            onClick = onClick,
        ).clip(RoundedCornerShape(Radius.lg)).background(AutoLogTheme.colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = state.receipt?.thumbUrl,
            contentDescription = stringResource(R.string.fuel_receipt_image),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            loading = { ImagePlaceholder(stringResource(R.string.fuel_receipt_image)) },
            error = { ImagePlaceholder(stringResource(R.string.fuel_receipt_image_unavailable)) },
        )
    }
}

@Composable
private fun ImagePlaceholder(text: String) {
    Box(Modifier.fillMaxSize().padding(Space.s5), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = AutoLogTheme.type.body,
            color = AutoLogTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
