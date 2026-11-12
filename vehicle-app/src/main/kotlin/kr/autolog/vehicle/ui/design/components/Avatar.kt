package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.AvatarGradients
import kr.autolog.vehicle.ui.design.Size

/** 디자인 언어 5.4: userId 해시로 8색 중 하나. */
fun avatarGradientIndex(userId: String): Int = Math.floorMod(userId.hashCode(), AvatarGradients.size)

/** 한글 = 첫 글자, 라틴 = 앞 두 단어의 첫 글자(대문자). */
fun initialsOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed.first()
    return if (first.code < 128 && first.isLetter()) {
        trimmed.split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }
    } else {
        first.toString()
    }
}

/** 아바타 모서리(크기의 20%). 사람 아바타와 아이콘 아바타가 같은 네모로 보이게 하는 단일 출처. */
fun avatarShape(size: Dp): Shape = RoundedCornerShape(size * 0.2f)

/** 사람 아바타: userId 해시로 고른 그라데이션 + 이니셜. */
@Composable
fun Avatar(userId: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val (start, end) = AvatarGradients[avatarGradientIndex(userId)]
    AvatarFace(
        initialsOf(name),
        Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset.Infinite),
        size,
        modifier,
    )
}

/** 아바타 얼굴(바탕 + 흰 이니셜). 이니셜 크기는 아바타 크기의 40%(토큰이 아니라 비율 — 아바타 크기 자체가 토큰). */
@Composable
fun AvatarFace(initials: String, background: Brush, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(avatarShape(size)).background(background), contentAlignment = Alignment.Center) {
        Text(
            initials,
            style = AutoLogTheme.type.display.copy(
                fontSize = (size.value * 0.4f).sp,
                lineHeight = (size.value * 0.45f).sp,
            ),
            color = Color.White,
        )
    }
}

/**
 * 사람 대신 아이콘이 들어간 아바타 크기 네모 — 운전자 미선택, 새 운전자 추가, 다른 사람.
 * 아이콘 의미는 옆·아래 글자가 말하므로 contentDescription은 두지 않는다.
 */
@Composable
fun IconAvatar(icon: ImageVector, size: Dp, modifier: Modifier = Modifier, iconSize: Dp = Size.icon) {
    val c = AutoLogTheme.colors
    Box(modifier.size(size).clip(avatarShape(size)).background(c.surface2), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(iconSize))
    }
}
