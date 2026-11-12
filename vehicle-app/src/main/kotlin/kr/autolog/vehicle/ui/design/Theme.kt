package kr.autolog.vehicle.ui.design

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalAutoLogColors = staticCompositionLocalOf { DarkColors }
val LocalAutoLogType = staticCompositionLocalOf { autoLogType() }
val LocalMotion = staticCompositionLocalOf { MotionTokens.Standard }

/** 스펙 6.9 4번: tier != FULL 또는 FULL 진입 직후 300 ms. 모든 상호작용 컴포넌트가 읽는다. */
val LocalInputLocked = compositionLocalOf { false }

/**
 * 주차가 아니다(정차·주행, tier != FULL — 스펙 1.1·D-31). 화면은 주차 때와 같지만 [LocalInputLocked]로 잠기고,
 * 잠긴 동작은 흐리게 보인다. 예외: 주행 중에도 쓸 동작([기록 종료])은 `tappable(whileDriving = true)`로 연다.
 */
val LocalDrivingLocked = compositionLocalOf { false }

/**
 * 차가 달리는 중이다(tier = BACKGROUND). 기록 중 경로 화살표(`RouteFlow`)는 이때만 위→아래로 흐른다 — 주차·정차 중엔 멈춘 그림
 * (2026-11-11 사용자 결정 D-32: 흐름은 "지금 움직이는 중"이라는 뜻이라 서 있을 때 흐르면 틀린 말이 된다).
 */
val LocalVehicleMoving = compositionLocalOf { false }

/**
 * 사라지는 중인 화면·팝업 안인가(디자인 언어 8.1 — 나가는 화면은 누를 수 없다). [LocalInputLocked]와 달리 모양은 그대로 두고
 * 눌림만 막는다 — 버튼이 사라지는 동안 비활성 색으로 깜빡이지 않게. `tappable`이 읽는다.
 */
val LocalLeaving = compositionLocalOf { false }

/**
 * 플랫폼 키보드 규제(`CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD`, `VehicleUsageRepository.noKeyboard`). 켜지면 글자 입력 칸만
 * 비활성 + "지금은 입력할 수 없어요"(스펙 7.9) — 버튼·탭은 그대로 쓴다([LocalInputLocked]와 다른 점).
 */
val LocalKeyboardLocked = compositionLocalOf { false }

object AutoLogTheme {
    val colors: AutoLogColors
        @Composable @ReadOnlyComposable
        get() = LocalAutoLogColors.current
    val type: AutoLogType
        @Composable @ReadOnlyComposable
        get() = LocalAutoLogType.current
    val motion: MotionTokens
        @Composable @ReadOnlyComposable
        get() = LocalMotion.current

    /** 창 크기에 따라 달라지는 글자 역할([WindowType]). */
    val windowType: WindowType
        @Composable @ReadOnlyComposable
        get() = windowTypeOf(LocalWindowLayout.current, LocalAutoLogType.current)
}

@Composable
private fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** 디자인 언어 5.5 M3 매핑. 화면 코드는 MaterialTheme이 아니라 AutoLogTheme.colors를 쓴다. */
private fun AutoLogColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = accentFill,
        onPrimary = onAccent,
        background = bg,
        surface = bg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface2,
        surfaceContainerHigh = surface3,
        onBackground = textPrimary,
        onSurface = textPrimary,
        onSurfaceVariant = textSecondary,
        outline = outlineStrong,
        outlineVariant = outline,
        error = danger,
        onError = onAccent,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accentFill,
        onPrimary = onAccent,
        background = bg,
        surface = bg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface2,
        surfaceContainerHigh = surface3,
        onBackground = textPrimary,
        onSurface = textPrimary,
        onSurfaceVariant = textSecondary,
        outline = outlineStrong,
        outlineVariant = outline,
        error = danger,
        onError = onAccent,
        scrim = scrim,
    )
}

@Composable
fun AutoLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    staticMotion: Boolean = false,
    /** false면 반복 상태 모션까지 멈춘다(스크린샷 — 무한 애니메이션이 캡처를 막는다). */
    ambientMotion: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val type = remember { autoLogType() }
    val motion =
        when {
            !ambientMotion || systemAnimationsOff() -> MotionTokens.Still
            staticMotion -> MotionTokens.Static
            else -> MotionTokens.Standard
        }
    CompositionLocalProvider(
        LocalAutoLogColors provides colors,
        LocalAutoLogType provides type,
        LocalMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = autoLogMaterialTypography(type),
            content = content,
        )
    }
}
