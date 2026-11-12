package kr.autolog.vehicle.ui.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 디자인 언어 8.2 motion.hero: 프로필 타일 → Home 운전자 칩. 정지형이거나 스코프가 없으면 아무것도 안 한다. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAvatar(userId: String): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedScope.current ?: return this
    val motion = AutoLogTheme.motion
    if (motion.isStatic) return this
    return with(transition) {
        this@sharedAvatar.sharedElement(
            state = rememberSharedContentState(key = "avatar-$userId"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> motion.smooth() },
        )
    }
}
