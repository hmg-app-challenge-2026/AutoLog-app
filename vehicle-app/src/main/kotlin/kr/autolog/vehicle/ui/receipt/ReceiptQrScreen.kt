package kr.autolog.vehicle.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.clockText
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.QrImage
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.screenMargin

/** QR 한 변 = 화면 짧은 변의 이 비율(스펙 7.4 — 폰 카메라가 차 화면 반사 속에서도 읽게). */
private const val QR_SHORT_SIDE_RATIO = 0.45f

@Composable
fun ReceiptQrRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { ReceiptQrViewModel(graph.receipts, graph.navigator, graph.clock) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    ReceiptQrScreen(state, vm::onAction)
}

/**
 * 주유 기록 추가의 기본 경로(스펙 7.4): QR → 안내 한 줄 → 남은 시간, 맨 아래 [직접 입력하기].
 * 영수증이 도착하면 ViewModel이 입력 창으로 바꾼다.
 */
@Composable
fun ReceiptQrScreen(state: ReceiptQrUiState, onAction: (ReceiptQrAction) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().padding(screenMargin())) {
        val qrSize = minOf(maxWidth, maxHeight) * QR_SHORT_SIDE_RATIO
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScreenHeader(stringResource(R.string.fuel_qr_title)) { onAction(ReceiptQrAction.Back) }
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val url = state.url
                when {
                    state.loading -> CircularProgressIndicator(color = AutoLogTheme.colors.accent)
                    url != null && !state.expired -> QrBody(url, qrSize, state.remainingSec)
                    else -> QrProblem(expired = state.expired, onRetry = { onAction(ReceiptQrAction.Retry) })
                }
            }
            SecondaryButton(
                stringResource(R.string.receipt_manual_entry),
                onClick = { onAction(ReceiptQrAction.ManualEntry) },
                modifier = Modifier.widthIn(max = Size.dialogMaxWidth).fillMaxWidth(),
            )
        }
    }
}

/** QR → 안내 → 남은 시간(1초마다 숫자만 바뀜). */
@Composable
private fun QrBody(url: String, qrSize: Dp, remainingSec: Int) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    QrImage(url, qrSize)
    Spacer(Modifier.height(Space.s5))
    Text(
        stringResource(R.string.receipt_qr_hint),
        style = t.caption,
        color = c.textSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Space.s4))
    Text(clockText(remainingSec), style = t.title, color = c.textPrimary)
}

/** 만료 또는 발급 실패: 이유 한 줄 + [새 QR 코드 만들기]/[다시 시도]. [직접 입력하기]는 그대로 아래에 있다. */
@Composable
private fun QrProblem(expired: Boolean, onRetry: () -> Unit) {
    Text(
        stringResource(if (expired) R.string.receipt_qr_expired else R.string.receipt_qr_failed),
        style = AutoLogTheme.type.body,
        color = AutoLogTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Space.s5))
    PrimaryButton(stringResource(if (expired) R.string.receipt_qr_new else R.string.action_retry), onClick = onRetry)
}
