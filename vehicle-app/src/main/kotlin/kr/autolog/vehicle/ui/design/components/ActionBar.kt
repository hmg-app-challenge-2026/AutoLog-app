package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.twoColumns

/**
 * [ActionBar]의 버튼 하나. [primary] = 주 동작(파란 채움 — 자동 진행 화면이면 시간이 다 될 때 저절로 눌릴 버튼).
 * [danger] = 되돌리기 어려운 동작(빨간 채움 — 운행 삭제, D-39). 누르면 바로 실행하지 말고 확인 팝업을 띄운다.
 */
data class BarAction(
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val danger: Boolean = false,
)

/**
 * 화면 맨 아래 행동 버튼 줄(디자인 언어 11장 ActionBar) — 운행 결과·주유 입력이 같은 모양을 쓴다.
 * 두 칸 배치(넓은 창·분할 화면 한 칸)는 같은 폭 버튼(하나당 최대 [Size.actionButtonMaxWidth])을 한 줄에 — 넓은 화면 끝에서
 * 끝까지 늘어나지 않게. 줄 자체는 폭을 채우지 않으므로 부르는 쪽이 가운데에 둔다(`FadeThrough`로 감싸도 상한이 지켜지게).
 * 세로로 긴 창은 셋이면 앞 둘을 한 줄 + 주 버튼 전체 폭, 둘 이하면 한 줄.
 */
@Composable
fun ActionBar(actions: List<BarAction>, modifier: Modifier = Modifier) {
    if (LocalWindowLayout.current.twoColumns) {
        val gap = Space.s4
        Row(
            modifier.widthIn(max = Size.actionButtonMaxWidth * actions.size + gap * (actions.size - 1)).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            actions.forEach { BarButton(it, Modifier.weight(1f)) }
        }
    } else {
        val rows = if (actions.size > 2) listOf(actions.dropLast(1), actions.takeLast(1)) else listOf(actions)
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                    row.forEach { BarButton(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BarButton(action: BarAction, modifier: Modifier) {
    if (action.danger) {
        DangerButton(action.label, onClick = action.onClick, modifier = modifier, enabled = action.enabled)
    } else if (action.primary) {
        PrimaryButton(
            action.label,
            onClick = action.onClick,
            modifier = modifier,
            height = Size.buttonMd,
            loading = action.loading,
            enabled = action.enabled,
        )
    } else {
        SecondaryButton(action.label, onClick = action.onClick, modifier = modifier, enabled = action.enabled)
    }
}
