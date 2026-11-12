package kr.autolog.vehicle.ui.result

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.SuccessCheck
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.design.twoColumns

/**
 * 저장 완료(스펙 7.3 — 5초가 다 되거나 [저장하기]로 저장을 마친 뒤). 가운데 체크(`SuccessCheck`가 그려지며 등장) →
 * 그 아래 "운행 기록을 저장했어요" → 맨 아래 [확인](운행 결과의 [저장하기]와 같은 자리·폭).
 * 체크를 **다 그린 뒤** 1초([SAVED_HOLD_MS])가 지나면 저절로 들어온 화면으로 — 시간은 ViewModel이 센다([TripResultAction.CheckDrawn]).
 * [확인]은 기다리지 않고 바로 닫는다.
 */
@Composable
internal fun TripSaved(onAction: (TripResultAction) -> Unit, modifier: Modifier = Modifier) {
    val margin = screenMargin()
    // 아래 여백은 운행 결과 화면과 같게 — [확인]이 방금 누른 [저장하기] 자리에 그대로 나타난다
    val bottom = if (LocalWindowLayout.current.twoColumns) Space.s4 else margin
    Column(
        modifier.fillMaxSize().padding(start = margin, top = margin, end = margin, bottom = bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        SuccessCheck(onDrawn = { onAction(TripResultAction.CheckDrawn) })
        Spacer(Modifier.height(Space.s7))
        SavedMessage()
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            stringResource(R.string.action_confirm),
            onClick = { onAction(TripResultAction.Close) },
            modifier = Modifier.widthIn(max = Size.actionButtonMaxWidth).fillMaxWidth(),
            height = Size.buttonMd,
        )
    }
}

/** "운행 기록을 저장했어요" — 체크를 긋기 시작할 때 아래에서 살짝 떠오르며 나타난다(정지형이면 처음부터). */
@Composable
private fun SavedMessage() {
    val motion = AutoLogTheme.motion
    val shown = remember { Animatable(if (motion.isStatic) 1f else 0f) }
    val rise = with(LocalDensity.current) { Space.s4.toPx() }
    LaunchedEffect(Unit) {
        if (motion.isStatic) return@LaunchedEffect
        delay((motion.successRingDelayMs + motion.successStrokeDelayMs).toLong())
        shown.animateTo(1f, motion.smooth())
    }
    Text(
        stringResource(R.string.result_saved),
        modifier = Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * rise
        },
        textAlign = TextAlign.Center,
        style = AutoLogTheme.windowType.screenTitle,
        color = AutoLogTheme.colors.textPrimary,
    )
}
