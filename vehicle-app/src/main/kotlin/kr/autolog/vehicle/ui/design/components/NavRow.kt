package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.PopVisibility
import kr.autolog.vehicle.ui.design.motion.animateSelectColor

/**
 * 디자인 언어 11장 NavRow — 다른 화면·상세로 들어가는 목록 줄(설정의 섹션 목록). 왼쪽 아이콘(`icon` 36 dp, `accent`) ·
 * 이름(`label`) + 아래 지금 상태 한 줄(`caption`, `textSecondary`) · 오른쪽 ›. 면 `surface1`, `radius.md`, 최소 76 dp, 줄 전체가 터치 대상.
 * [selected] = 넓은 창 마스터–디테일에서 오른쪽에 열린 섹션 — `accent` 테두리(3 dp)로 표시하고 ›는 숨긴다.
 */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val shape = RoundedCornerShape(Radius.md)
    // 넓은 창에서 다른 섹션을 고르면 테두리가 교차하며 옮겨 간다
    val edge by animateSelectColor(if (selected) c.accent else Color.Transparent)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .semantics { this.selected = selected }
            .tappable(onClick)
            .clip(shape)
            .background(c.surface1)
            .border(Size.ring, edge, shape)
            .padding(horizontal = Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(Size.icon))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.label, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            summary?.let {
                Text(it, style = t.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PopVisibility(!selected) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textTertiary)
        }
    }
}
