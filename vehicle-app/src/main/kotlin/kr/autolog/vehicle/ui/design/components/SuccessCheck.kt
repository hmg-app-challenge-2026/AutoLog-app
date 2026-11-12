package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size

/**
 * 디자인 언어 11장 SuccessCheck — "끝났다"를 한 번에 알리는 완료 표시(운행 기록 저장 완료). 순서(`motion.success*`, 약 1초):
 * 1. 원이 0에서 살짝 튀어 오르며 커진다(`successCircle` — 덜 감쇠된 스프링이라 한 번 넘쳤다 자리 잡는다).
 * 2. 원이 자리 잡을 즈음 같은 색 테두리가 한 번 퍼지며 사라진다(`successRingMs`) — 반복하지 않는다.
 * 3. 흰 체크가 **왼쪽 짧은 획 → 오른쪽 긴 획** 순서로 손으로 긋듯 그려진다(`successStrokeMs`, 끝에서 천천히).
 * 4. 다 그려지면 원 전체가 한 번 톡 커졌다 돌아온다(`successPop`) — 그 순간 [onDrawn]. 진동은 넣지 않는다
 *    (차량 디스플레이엔 진동 장치가 없는 경우가 많고, 있어도 완료 알림엔 필요 없다 — 2026-11-10 사용자 결정).
 * 정지형 모션(정차·애니메이션 줄이기·스크린샷)이면 완성된 모양을 바로 그리고 [onDrawn]도 바로 부른다.
 */
@Composable
fun SuccessCheck(
    onDrawn: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Size.successBadge,
    description: String? = null,
) {
    val motion = AutoLogTheme.motion
    val c = AutoLogTheme.colors
    val drawn by rememberUpdatedState(onDrawn)
    val start = if (motion.isStatic) 1f else 0f
    val circle = remember { Animatable(start) }
    val ring = remember { Animatable(if (motion.isStatic) 1f else 0f) }
    val stroke = remember { Animatable(start) }
    val pop = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (motion.isStatic) {
            drawn()
            return@LaunchedEffect
        }
        launch { circle.animateTo(1f, motion.successCircle()) }
        delay(motion.successRingDelayMs.toLong())
        launch { ring.animateTo(1f, motion.successRing()) }
        delay(motion.successStrokeDelayMs.toLong())
        stroke.animateTo(1f, motion.successStroke())
        pop.animateTo(SUCCESS_POP_SCALE, motion.successPopOut())
        pop.animateTo(1f, motion.successPopBack())
        drawn()
    }

    val accent = c.accentFill
    val mark = c.onAccent
    val path = remember { Path() }
    val partial = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(
        modifier
            .size(size)
            .semantics { description?.let { contentDescription = it } },
    ) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        // 2. 한 번 퍼지는 테두리 — 원 크기에서 1.5배까지, 퍼질수록 옅고 가늘게
        if (ring.value in 0.001f..0.999f) {
            val t = ring.value
            drawCircle(
                color = accent.copy(alpha = (1f - t) * RING_ALPHA),
                radius = r * (1f + t * RING_GROWTH),
                center = center,
                style = Stroke(width = r * RING_WIDTH * (1f - t)),
            )
        }
        scale(circle.value * pop.value, pivot = center) {
            drawCircle(color = accent, radius = r, center = center)
            if (stroke.value > 0f) {
                // 체크: 왼쪽 위 → 아래 꼭짓점(짧은 획) → 오른쪽 위(긴 획). 원 안쪽 비율로 잡아 크기와 무관
                path.reset()
                path.moveTo(center.x - r * 0.40f, center.y + r * 0.02f)
                path.lineTo(center.x - r * 0.10f, center.y + r * 0.32f)
                path.lineTo(center.x + r * 0.44f, center.y - r * 0.28f)
                measure.setPath(path, false)
                partial.reset()
                measure.getSegment(0f, measure.length * stroke.value, partial, true)
                drawPath(
                    partial,
                    color = mark,
                    style = Stroke(width = r * CHECK_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** 다 그린 뒤 원이 한 번 톡 커지는 비율. */
private const val SUCCESS_POP_SCALE = 1.06f

/** 퍼지는 테두리: 시작 투명도·최대 크기(반지름 대비 +50%)·시작 두께(반지름 대비). */
private const val RING_ALPHA = 0.45f
private const val RING_GROWTH = 0.5f
private const val RING_WIDTH = 0.12f

/** 체크 획 두께(반지름 대비) — 원 크기가 바뀌어도 같은 인상. */
private const val CHECK_WIDTH = 0.17f
