package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.motion.animateSelectColor

/**
 * 디자인 언어 11장 DockTile — 화면 맨 아래 동작. **아이콘 네모 + 그 아래 이름**(앱 아이콘·테슬라 하단 바 방식).
 * 화면 폭에 따라 112/96/88 dp 네모 안에 아이콘, 아래 이름을 놓는다. 터치 영역은 네모 + 이름 칸 전체(76 dp 이상).
 * [emphasized]면 네모가 파란 채움(`accentFill` + `onAccent` 아이콘), 이름은 `accent` — 같은 자리에서 상태가 바뀐 주 동작(기록 중의 [기록 종료]).
 * 폭은 부모가 정한다(보통 `weight(1f)`로 등분 — 네모가 같은 간격으로 놓인다).
 */
@Composable
fun DockTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    whileDriving: Boolean = false,
) {
    val c = AutoLogTheme.colors
    // 정차·주행 중엔 같은 자리·같은 크기로 흐리게(눌리지 않음, D-31) — [whileDriving]인 타일만 그대로
    val dimmed = LocalDrivingLocked.current && !whileDriving
    val layout = LocalWindowLayout.current
    val iconSize = when (layout) {
        WindowLayout.WIDE, WindowLayout.NARROW -> Size.dockIcon
        WindowLayout.TALL -> Size.dockIconTall
    }
    Column(
        modifier
            .widthIn(min = Size.touchMin)
            .tappable(onClick, whileDriving = whileDriving)
            .clip(RoundedCornerShape(Radius.md)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        // 같은 자리에서 동작이 바뀌면([기록 시작] → [기록 종료]) 색은 교차하고 아이콘·이름은 그 자리에서 바뀐다
        val face by animateSelectColor(if (emphasized) c.accentFill else c.surface1)
        val tint by animateSelectColor(
            when {
                emphasized -> c.onAccent
                dimmed -> c.textTertiary
                else -> c.accent
            },
        )
        val labelColor by animateSelectColor(
            when {
                emphasized -> c.accent
                dimmed -> c.textTertiary
                else -> c.textSecondary
            },
        )
        Box(
            Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(Radius.lg))
                .background(face),
            contentAlignment = Alignment.Center,
        ) {
            FadeThrough(icon, contentAlignment = Alignment.Center, label = "dockIcon") {
                Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(Size.iconLg))
            }
        }
        FadeThrough(label, contentAlignment = Alignment.TopCenter, label = "dockLabel") {
            Text(
                it,
                style = AutoLogTheme.windowType.dockLabel,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
