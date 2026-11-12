package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.LocalLeaving
import kr.autolog.vehicle.ui.design.confirmHaptic

/**
 * **앱의 모든 누를 수 있는 요소는 이것 하나로 누른다.** 한곳에서 지키는 규칙:
 * - 입력 잠금(`LocalInputLocked` — 주행·정차 중, FULL 진입 직후 300 ms)이면 눌리지 않는다(스펙 6.9).
 *   [whileDriving]이면 주행·정차 잠금은 건너뛰고 진입 직후 잠금만 따른다 — [기록 종료]처럼 차가 움직여도 써야 하는 동작(D-31).
 *   사라지는 중인 화면·팝업 안(`LocalLeaving`)이어도 눌리지 않는다(모양은 그대로).
 * - 누름 피드백: 0.97배 tween(90) / 뗌 spring.snappy, 정지형이면 크기 변화 없음(디자인 언어 8.2 press).
 * - 확인 햅틱(디자인 언어 8.3), 물결(ripple)은 쓰지 않는다.
 *
 * [onClick]이 null이면 아무것도 하지 않는다(표시 전용). [enabled]가 false면 그 요소만 비활성.
 * **clip·background보다 앞에** 붙인다 — 누름 배율이 면까지 같이 줄어들고, 터치 영역은 그 시점의 크기(안쪽 padding 앞)다.
 */
fun Modifier.tappable(
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    role: Role = Role.Button,
    whileDriving: Boolean = false,
): Modifier {
    if (onClick == null) return this
    return composed {
        val interaction = remember { MutableInteractionSource() }
        val view = LocalView.current
        // 진입 직후 잠금은 주차(FULL)에서만 걸리므로, 주행 잠금을 뺀 나머지 = 잠김 && 주차
        val locked = LocalInputLocked.current && !(whileDriving && LocalDrivingLocked.current)
        pressFeedback(interaction).clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled && !locked && !LocalLeaving.current,
            role = role,
        ) {
            view.confirmHaptic()
            onClick()
        }
    }
}

private fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val motion = AutoLogTheme.motion
    val scale by animateFloatAsState(
        targetValue = if (pressed) motion.pressScale else 1f,
        animationSpec = if (pressed) tween(90) else motion.snappy(),
        label = "press",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) motion.pressAlpha else 1f,
        animationSpec = if (pressed) tween(90) else motion.quick(),
        label = "pressAlpha",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
