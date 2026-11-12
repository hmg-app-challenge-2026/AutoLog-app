package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

/**
 * 디자인 언어 11장 EdgeCountdownBar — 화면 맨 위 가장자리의 가는 막대(4 dp, `accent`, 양 끝 둥글게). 좌우 화면 끝에서 16 dp 띄운다
 * (2560 dp 화면 끝에서 끝까지 닿으면 넓대대해 보였다 — 2026-11-10 사용자 지시). 자동 진행까지 남은 비율(1 → 0)만큼
 * 왼쪽부터 채워 있고 **오른쪽 끝이 왼쪽으로 줄어든다**. 바탕 줄 없이 막대만 — 화면 위 정보와 경쟁하지 않는 시간 단서.
 * 남은 시간(ms)을 받아 **마감 순간에 정확히 0**이 되도록 선형으로 줄인다(Pleos 애니메이션 원칙 Linear, 상태 설명용) — 막대가 다 사라질 때
 * 화면이 넘어간다. 정지형이면 지금 남은 비율만 그린다.
 */
@Composable
fun EdgeCountdownBar(remainingMs: Long, totalMs: Long, modifier: Modifier = Modifier) {
    val motion = AutoLogTheme.motion
    val now = (remainingMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val shown = remember { Animatable(now) }
    // 새 남은 시간을 받을 때마다(1초마다) 그 값에 맞춘 뒤 마감까지 남은 시간 동안 0으로 곧게 줄인다 —
    // 마감 순간에 정확히 0이 되고, 시계가 조금 어긋나도 다음 값에서 바로잡힌다
    LaunchedEffect(remainingMs, totalMs, motion.isStatic) {
        shown.snapTo(now)
        if (!motion.isStatic && remainingMs > 0) {
            shown.animateTo(0f, tween(durationMillis = remainingMs.toInt(), easing = LinearEasing))
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = Space.s4)) {
        Box(
            Modifier.fillMaxWidth(
                shown.value,
            ).height(Size.edgeBar).clip(CircleShape).background(AutoLogTheme.colors.accent),
        )
    }
}
