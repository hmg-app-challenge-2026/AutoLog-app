package kr.autolog.vehicle.ui.design.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import kr.autolog.vehicle.ui.design.MotionTokens

/**
 * [NavTransition] → 화면 전환 모양(디자인 언어 8.2). 이동 거리는 px로 받는다(호출하는 쪽이 Space 토큰을 px로 바꿔 넘긴다).
 * 뒤 화면이 어두워지는 덮개는 여기서 못 그리므로 FullApp이 [behindDimTarget]으로 따로 그린다.
 */
fun <S> AnimatedContentTransitionScope<S>.routeTransform(
    kind: NavTransition,
    motion: MotionTokens,
    presentRisePx: Int,
    dismissDropPx: Int,
): ContentTransform {
    val offset = motion.smooth<IntOffset>(IntOffset.VisibilityThreshold)
    val parallax = motion.pushParallax
    return when (kind) {
        NavTransition.PUSH ->
            slideInHorizontally(offset) { it } togetherWith
                slideOutHorizontally(offset) { -(it * parallax).toInt() }
        NavTransition.POP ->
            (slideInHorizontally(offset) { -(it * parallax).toInt() } togetherWith slideOutHorizontally(offset) { it })
                .apply { targetContentZIndex = -1f } // 떠나는 화면이 위에서 빠진다
        NavTransition.PRESENT ->
            (
                slideInVertically(offset) { presentRisePx } +
                    scaleIn(motion.smooth(), initialScale = motion.presentScale) +
                    fadeIn(motion.quick())
                ) togetherWith ExitTransition.KeepUntilTransitionsFinished
        NavTransition.DISMISS ->
            (EnterTransition.None togetherWith (slideOutVertically(offset) { dismissDropPx } + fadeOut(motion.quick())))
                .apply { targetContentZIndex = -1f } // 내려가는 화면이 위
        NavTransition.FADE_THROUGH ->
            (fadeIn(motion.swapIn()) + scaleIn(motion.swapIn(), initialScale = motion.swapScale)) togetherWith
                fadeOut(motion.swapOut())
        NavTransition.NONE -> EnterTransition.None togetherWith ExitTransition.None
    }
}

/**
 * 뒤 화면 덮개의 목표 세기(0 = 없음, 1 = `pushBehindDim`만큼). 앞으로 갈 때 밀려나는 화면은 어두워지며 나가고(PostExit),
 * 뒤로 올 때 돌아오는 화면은 어두운 데서 밝아지며 들어온다(PreEnter). 자리에 있으면(Visible) 0.
 */
fun behindDimTarget(kind: NavTransition, state: EnterExitState): Float = when {
    state == EnterExitState.PostExit && kind == NavTransition.PUSH -> 1f
    state == EnterExitState.PreEnter && kind == NavTransition.POP -> 1f
    else -> 0f
}
