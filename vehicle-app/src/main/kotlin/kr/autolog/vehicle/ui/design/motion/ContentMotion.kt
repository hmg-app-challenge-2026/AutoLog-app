package kr.autolog.vehicle.ui.design.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space

// 화면 안의 작은 변화(디자인 언어 8.2 — 같은 자리 교체 · 방향 교체 · 펼침·접힘 · 선택).
// 화면·컴포넌트는 AnimatedContent·animate*AsState를 직접 쓰지 않고 이 도우미만 쓴다 — 모양과 스프링이 한곳에서 정해진다.

/**
 * **같은 자리 교체**: [target]이 바뀌면 옛 내용은 아주 빨리 사라지고(`swapOut`) 새 내용은 90 ms 뒤 0.98→1 + 8 dp 아래에서
 * 떠오르며(`swapIn`) 나타난다 — 둘이 겹쳐 읽히지 않는다.
 * 크기가 다르면 크기도 `smooth`로 맞춘다. [contentKey]가 같으면(같은 종류의 내용) 움직이지 않는다.
 * 주의: [modifier]로 폭·높이를 채우면 안쪽 내용의 최대 크기 제한(`widthIn(max)`)이 무시된다(최소 크기가 함께 내려가서) —
 * 가운데 정렬이 필요하면 바깥 `Box`가 맡고 이 상자는 내용 크기로 둔다.
 */
@Composable
fun <S> FadeThrough(
    target: S,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (S) -> Any? = { it },
    label: String = "fadeThrough",
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    val motion = AutoLogTheme.motion
    val rise = with(LocalDensity.current) { Space.s2.roundToPx() }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(motion.swapIn()) +
                    scaleIn(motion.swapIn(), initialScale = motion.swapScale) +
                    slideInVertically(motion.swapIn(IntOffset.VisibilityThreshold)) { rise }
                ) togetherWith fadeOut(motion.swapOut()) using
                SizeTransform(clip = false) { _, _ -> motion.smooth(IntSize.VisibilityThreshold) }
        },
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        label = label,
        content = content,
    )
}

/**
 * **방향 교체**(달 이동 ‹ ›): [direction]이 양수면(다음) 옛 내용은 왼쪽으로 밀려나고 새 내용이 오른쪽에서, 음수면 반대.
 * 이동은 48 dp(`smooth`), 투명도는 `quick`. [direction]은 옛 값 → 새 값 비교로 호출하는 쪽이 정한다(예: 달 차이).
 */
@Composable
fun <S> DirectionalContent(
    target: S,
    direction: (from: S, to: S) -> Int,
    modifier: Modifier = Modifier,
    label: String = "directional",
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    val motion = AutoLogTheme.motion
    val shift = with(LocalDensity.current) { Space.s7.roundToPx() }
    AnimatedContent(
        targetState = target,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val sign = direction(initialState, targetState).coerceIn(-1, 1)
            val offset = motion.smooth(IntOffset.VisibilityThreshold)
            (slideInHorizontally(offset) { sign * shift } + fadeIn(motion.quick())) togetherWith
                (slideOutHorizontally(offset) { -sign * shift } + fadeOut(motion.quick())) using
                // 자기 칸 안에서만 밀려난다(캡슐 화살표 위로 넘치지 않게 — iOS 피커처럼)
                SizeTransform(clip = true) { _, _ -> motion.smooth(IntSize.VisibilityThreshold) }
        },
        contentAlignment = Alignment.Center,
        label = label,
        content = content,
    )
}

/** **펼침·접힘**: 안내 줄·버튼처럼 생겼다 사라지는 것. 크기 `smooth` + 투명도 `quick`. */
@Composable
fun ExpandVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = AutoLogTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(motion.smooth(IntSize.VisibilityThreshold)) + fadeIn(motion.quick()),
        exit = shrinkVertically(motion.smooth(IntSize.VisibilityThreshold)) + fadeOut(motion.quick()),
    ) { content() }
}

/**
 * **안내 한 줄**: [value]가 생기면 펼쳐지고, 바뀌면 그 자리에서 교체되고, null이 되면 마지막 내용으로 접힌다
 * (결과 한 줄 · 단계 안내처럼 있다 없다 하는 문장).
 */
@Composable
fun <T : Any> ExpandSwap(value: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    ExpandVisibility(value != null, modifier) {
        last?.let { shown -> FadeThrough(shown, label = "expandSwap") { content(it) } }
    }
}

/** **선택 표시의 "톡"**: 라디오 점·체크가 나타날 때 0→1(`bouncy`), 사라질 때는 줄며(`quick`). */
@Composable
fun PopVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = AutoLogTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(motion.bouncy()) + fadeIn(motion.quick()),
        exit = scaleOut(motion.quick()) + fadeOut(motion.quick()),
    ) { content() }
}

/** **선택 색**: 테두리·바탕·글자 색이 바뀔 때 `quick`으로 교차한다. */
@Composable
fun animateSelectColor(target: Color, label: String = "selectColor"): State<Color> =
    animateColorAsState(target, AutoLogTheme.motion.quick(), label = label)

/** **선택 두께**: 테두리 두께가 바뀔 때(고르지 않음 1 dp → 고름 3 dp). */
@Composable
fun animateSelectDp(target: Dp, label: String = "selectDp"): State<Dp> =
    animateDpAsState(target, AutoLogTheme.motion.quick(Dp.VisibilityThreshold), label = label)
