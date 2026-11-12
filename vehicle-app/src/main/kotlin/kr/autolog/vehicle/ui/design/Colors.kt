package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 디자인 언어 5장. 값을 바꾸면 ContrastTest가 먼저 통과해야 한다. */
@Immutable
data class AutoLogColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentFill: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerFill: Color,
    val scrim: Color,
) {
    val surfaces: List<Color> get() = listOf(bg, surface1, surface2, surface3)

    /** 떠 있는 면(팝업 카드). 다크는 한 단계 밝은 surface2, 라이트는 흰 surface1 — 둘 다 [surfaces] 안이라 대비 검사를 그대로 받는다. */
    val surfaceRaised: Color get() = if (isDark) surface2 else surface1
}

val DarkColors =
    AutoLogColors(
        isDark = true,
        bg = Color(0xFF0A0B0D),
        surface1 = Color(0xFF131518),
        surface2 = Color(0xFF1B1E22),
        surface3 = Color(0xFF252930),
        outline = Color(0xFF2E333A),
        outlineStrong = Color(0xFF6B7280),
        textPrimary = Color(0xFFF4F5F7),
        textSecondary = Color(0xFFB0B6BF),
        textTertiary = Color(0xFF8D949E),
        accent = Color(0xFF5B9BFF),
        accentFill = Color(0xFF3268F8),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF3DD68C),
        warning = Color(0xFFF7B955),
        danger = Color(0xFFFF6B6B),
        dangerFill = Color(0xFFD93A3F),
        scrim = Color(0x8F000000), // 56%
    )

val LightColors =
    AutoLogColors(
        isDark = false,
        bg = Color(0xFFF4F5F7),
        surface1 = Color(0xFFFFFFFF),
        surface2 = Color(0xFFECEEF1),
        surface3 = Color(0xFFE1E4E9),
        outline = Color(0xFFD5D9DF),
        outlineStrong = Color(0xFF767D88),
        textPrimary = Color(0xFF0D0F12),
        textSecondary = Color(0xFF454C56),
        textTertiary = Color(0xFF59606B),
        accent = Color(0xFF1F55D1),
        accentFill = Color(0xFF2B63F0),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF0B6B3C),
        warning = Color(0xFF7A4E00),
        danger = Color(0xFFB02525),
        dangerFill = Color(0xFFD93A3F),
        scrim = Color(0x52000000), // 32%
    )

/** 디자인 언어 5.4. userId 해시로 고른다(Avatar.kt). */
val AvatarGradients: List<Pair<Color, Color>> =
    listOf(
        Color(0xFF3B5BDB) to Color(0xFF1E2F8C),
        Color(0xFF7048E8) to Color(0xFF3A1F8F),
        Color(0xFFC2255C) to Color(0xFF6E1236),
        Color(0xFFC2410C) to Color(0xFF8A2E06),
        Color(0xFF237A36) to Color(0xFF134A20),
        Color(0xFF0B7285) to Color(0xFF064B57),
        Color(0xFF5C6B7A) to Color(0xFF2C343D),
        Color(0xFFB5447F) to Color(0xFF5E1F44),
    )
