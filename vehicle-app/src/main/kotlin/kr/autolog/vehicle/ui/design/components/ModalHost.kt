package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kr.autolog.vehicle.ui.design.AutoLogTheme

/**
 * 팝업이 지금 얼마나 떠 있는가 — [progress] 0(없음) → 1(다 열림), [closing]이면 닫히는 중.
 * [ModalCard]가 읽어 scrim·카드 배율·투명도를 그린다. [ModalHost] 밖에서 그린 ModalCard는 늘 다 열린 상태(움직임 없음).
 */
@Immutable
data class ModalPresence(val progress: Float, val closing: Boolean)

val LocalModalPresence = staticCompositionLocalOf { ModalPresence(progress = 1f, closing = false) }

/**
 * 디자인 언어 8.2 팝업 — **열릴 때도 닫힐 때도 움직인다**. [value]가 null이 아니면 열리고, null이 되면 닫힌다.
 * 닫히는 동안에는 마지막 값으로 계속 그려서(화면 상태는 이미 null이어도 된다) 카드가 줄며 사라진다.
 * 열림은 `smooth`, 닫힘은 `quick`(사라짐은 나타남보다 빨리). 정지형이면 즉시. 팝업은 모두 이 함수로 감싼다.
 */
@Composable
fun <T : Any> ModalHost(value: T?, content: @Composable (T) -> Unit) {
    val motion = AutoLogTheme.motion
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    val open = value != null
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) { progress.animateTo(if (open) 1f else 0f, if (open) motion.smooth() else motion.quick()) }
    // 다 닫혔으면 그리지 않는다(닫히는 동안은 마지막 값으로 계속 그린다)
    if (!open && progress.value == 0f) return
    val shown = last ?: return
    CompositionLocalProvider(LocalModalPresence provides ModalPresence(progress.value, closing = !open)) {
        content(shown)
    }
}

/** [ModalHost]의 참·거짓판 — 따로 넘길 값이 없는 팝업(이미지 크게 보기 등). */
@Composable
fun ModalHost(visible: Boolean, content: @Composable () -> Unit) {
    ModalHost(value = if (visible) Unit else null) { content() }
}
