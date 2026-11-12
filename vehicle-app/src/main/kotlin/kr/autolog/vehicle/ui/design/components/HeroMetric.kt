package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space

/** 숫자 크기 3단계: HERO 140 sp / MEDIUM 112 sp / COMPACT 80 sp(긴 누적거리, 좁은 창). */
enum class MetricSize { HERO, MEDIUM, COMPACT }

/** 값이 바뀌었을 때 새 숫자가 이 투명도에서 1로 돌아온다(motion.quick). 정지형이면 즉시 교체. */
private const val NUMBER_CHANGE_ALPHA = 0.4f

/**
 * 숫자 + 단위 + 라벨. [value]가 null이면 숫자 대신 [unavailableText](추정값을 그리지 않는다).
 * 숫자는 항상 Text로 그려 베이스라인 정렬을 유지한다(AnimatedContent는 베이스라인이 없고 자기 영역을 자른다).
 */
@Composable
fun HeroMetric(
    value: String?,
    unit: String,
    label: String?,
    unavailableText: String,
    modifier: Modifier = Modifier,
    size: MetricSize = MetricSize.HERO,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val motion = AutoLogTheme.motion
    Column(modifier) {
        if (label != null) Text(label, style = t.body, color = c.textSecondary)
        if (value == null) {
            Text(unavailableText, style = t.title, color = c.warning)
        } else {
            val alpha = remember { Animatable(1f) }
            val shown = remember { mutableListOf(value) }
            LaunchedEffect(value) {
                if (shown.last() != value) {
                    shown[0] = value
                    if (!motion.isStatic) {
                        alpha.snapTo(NUMBER_CHANGE_ALPHA)
                        alpha.animateTo(1f, motion.quick())
                    }
                }
            }
            Row {
                val numberStyle =
                    when (size) {
                        MetricSize.HERO -> t.hero
                        MetricSize.MEDIUM -> t.heroSmall
                        MetricSize.COMPACT -> t.heroCompact
                    }
                // 숫자·단위는 줄바꿈하지 않는다("k / m" 방지). 넘치면 크기 단계를 낮춰 해결한다.
                Text(
                    value,
                    style = numberStyle,
                    color = c.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline().graphicsLayer { this.alpha = alpha.value },
                )
                Spacer(Modifier.width(Space.s3))
                Text(
                    unit,
                    style = t.headline,
                    color = c.textTertiary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}
