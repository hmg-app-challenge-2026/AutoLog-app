package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.sharedAvatar

/** 디자인 언어 11장 ProfileTile. 1탭 = 선택 + 진입(조작 최소화 2.6). */
@Composable
fun ProfileTile(
    userId: String,
    name: String,
    dept: String?,
    scheduled: Boolean,
    recent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp = Size.profileTileNarrow,
    /** shared element 키. 같은 사람이 한 화면에 두 번 나오면 한쪽은 null로 준다(색은 userId로 유지). */
    sharedKey: String? = userId,
) {
    val c = AutoLogTheme.colors
    TileFrame(name, AutoLogTheme.windowType.tileName, c.textPrimary, dept, tileSize, onClick, modifier) {
        // 배지는 아바타 아래 가장자리에 걸친다 — 이니셜을 가리지 않는다
        Box(contentAlignment = Alignment.BottomCenter) {
            val ring =
                if (scheduled) {
                    Modifier.border(Size.ring, c.accent, RoundedCornerShape(Radius.xl)).padding(Space.s2)
                } else {
                    Modifier.padding(Space.s2)
                }
            // 아래 여백(배지 자리)은 링 바깥에 둔다 — 링이 아바타와 같은 정사각형으로 남게
            val framed = Modifier.padding(bottom = Space.s3).then(ring)
            Avatar(userId, name, tileArt(tileSize), if (sharedKey != null) framed.sharedAvatar(sharedKey) else framed)
            if (scheduled || recent) {
                TileBadge(
                    stringResource(if (scheduled) R.string.badge_scheduled else R.string.badge_recent),
                    emphasized = scheduled,
                )
            }
        }
    }
}

/**
 * 사람 타일과 같은 격자·크기의 동작 타일 — [다른 사람], [+ 새 운전자 추가].
 * 아바타 자리에 [IconAvatar], 이름 자리에 동작 이름(보조색)이 온다.
 */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp = Size.profileTileNarrow,
) {
    // 동작 이름은 사람 이름보다 길다("새 운전자 추가") — 한 단계 작은 글자, 두 줄까지
    TileFrame(
        label,
        AutoLogTheme.windowType.tileAction,
        AutoLogTheme.colors.textSecondary,
        null,
        tileSize,
        onClick,
        modifier,
        maxLines = 2,
    ) {
        // 사람 타일의 링·배지 여백과 같게 둬서 아이콘 네모와 이름 줄이 옆 타일과 정렬된다
        IconAvatar(icon, tileArt(tileSize), Modifier.padding(bottom = Space.s3).padding(Space.s2), Size.iconLg)
    }
}

/** 타일 그림(아바타·아이콘 네모) 크기. */
private fun tileArt(tileSize: Dp): Dp = tileSize - Space.s4

/** 타일 공통 뼈대: 폭 [tileSize], 누름 피드백 + 햅틱, 그림 아래 이름 한 줄(+ 보조 한 줄). */
@Composable
private fun TileFrame(
    title: String,
    titleStyle: TextStyle,
    titleColor: Color,
    caption: String?,
    tileSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
    maxLines: Int = 1,
    art: @Composable () -> Unit,
) {
    Column(
        modifier
            .width(tileSize)
            .tappable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        art()
        Text(
            title,
            style = titleStyle,
            color = titleColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (caption != null) {
            Text(
                caption,
                style = AutoLogTheme.windowType.tileCaption,
                color = AutoLogTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** 타일 배지(배차·최근). 배차는 강조(accentFill + 흰 글씨 4.7:1), 최근은 surface3 + textSecondary. */
@Composable
private fun TileBadge(text: String, emphasized: Boolean) {
    val c = AutoLogTheme.colors
    Text(
        text,
        style = AutoLogTheme.type.caption,
        color = if (emphasized) c.onAccent else c.textSecondary,
        modifier =
        Modifier
            .clip(CircleShape)
            .background(if (emphasized) c.accentFill else c.surface3)
            .padding(horizontal = Space.s3, vertical = Space.s1),
    )
}
