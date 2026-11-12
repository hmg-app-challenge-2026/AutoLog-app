package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.motion.animateSelectColor

/**
 * 디자인 언어 11장 StatusLine — 화면 맨 위 상태 한 줄. 배경 없는 점 + 글자: `● 기록 중 · 35분 경과`.
 * 점 색이 상태를 말하고(기록 중 `success`, 대기 `textTertiary`), 글자는 본문색, [detail]은 회색 보조.
 * 알약 배경이 없어 아래 주인공 숫자와 경쟁하지 않으면서, 맨 위 왼쪽 자리라 가장 먼저 읽힌다. 누를 수 없다.
 */
@Composable
fun StatusLine(text: String, dot: Color, modifier: Modifier = Modifier, detail: String? = null) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        // 상태가 바뀌면(대기 중 → 기록 중) 점 색은 교차하고 글자는 그 자리에서 바뀐다
        val dotColor by animateSelectColor(dot)
        Box(Modifier.size(Size.statusDot).clip(CircleShape).background(dotColor))
        // 상태 줄은 언제나 한 줄 — 폭이 모자라면 보조(detail) 끝을 말줄임(줄바꿈하면 배지 줄보다 높아진다)
        FadeThrough(text, label = "status") { Text(it, style = t.label, color = c.textPrimary, maxLines = 1) }
        if (detail != null) {
            Text("·", style = t.label, color = c.textTertiary)
            Text(
                detail,
                Modifier.weight(1f, fill = false),
                style = t.body,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
