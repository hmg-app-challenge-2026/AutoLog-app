package kr.autolog.vehicle.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import kr.autolog.vehicle.ui.design.motion.DelayedSpec

/**
 * 디자인 언어 8.2·8.4. [isStatic]은 tier != FULL(정차·주행 — 같은 화면 읽기 전용, D-31) 또는 시스템 "애니메이션 줄이기"일 때 — 화면 전환·등장 모션이 멈춘다.
 * [ambient]는 그와 별개인 **상태 표시용 반복 모션**(경로 화살표 흐름 등) 허용 여부. 정차 중에도 켜지고,
 * 시스템 "애니메이션 줄이기"이거나 스크린샷처럼 정지 화면이 필요할 때만 꺼진다.
 * 컴포넌트는 이 토큰만 쓰므로 단계가 바뀌면 자동으로 맞는 형태가 된다.
 */
@Immutable
class MotionTokens private constructor(val isStatic: Boolean, val ambient: Boolean) {
    // ── 스프링 세 가지(디자인 언어 8.2, 2026-11-10 사용자 결정 — iOS·Apple 방식) ──
    // 멈춘 상태에서 출발하는 스프링은 속도 0에서 가속해 정점을 찍고 길게 감속한다("느리게 출발 → 빠르게 → 천천히 끝").
    // 전환 도중 방향이 바뀌어도 지금 속도를 이어받아 자연스럽게 되돌아간다. 모든 움직임은 이 셋 중 하나다.

    /** 넘침 없음, 응답 ≈ 0.35 s(SwiftUI `.smooth`) — 화면 이동·띄우기·내용 교체·펼침·달 이동. */
    fun <T> smooth(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (isStatic) snap() else spring(dampingRatio = 1f, stiffness = SMOOTH_STIFFNESS, visibilityThreshold)

    /** 넘침 없음, 응답 ≈ 0.24 s — 사라짐·닫힘·투명도·색. 나타나는 쪽보다 빨리 끝나 새 내용에 시선이 간다. */
    fun <T> quick(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (isStatic) snap() else spring(dampingRatio = 1f, stiffness = QUICK_STIFFNESS, visibilityThreshold)

    /** 살짝 튐 — 손에 바로 반응하는 작은 "톡"(라디오 점·체크 표시)에만. 큰 면에는 쓰지 않는다. */
    fun <T> bouncy(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = if (isStatic) {
        snap()
    } else {
        spring(
            dampingRatio = BOUNCY_DAMPING,
            stiffness = BOUNCY_STIFFNESS,
            visibilityThreshold,
        )
    }

    // ── 모양(배율·비율) — 이동 거리 dp는 Space 토큰, 여기는 단위 없는 값만 ──

    /** 화면 앞으로 갈 때 뒤 화면이 왼쪽으로 밀리는 비율(폭 대비, iOS 패럴랙스). */
    val pushParallax: Float get() = 0.3f

    /** 밀린 뒤 화면 위 덮개의 최대 세기 — `scrim` 색의 이 비율(다크 ≈ 34%, 라이트 ≈ 19% 검정). */
    val pushBehindDim: Float get() = 0.6f

    /** 팝업 카드가 열릴 때 시작 배율 · 닫힐 때 끝 배율. */
    val modalEnterScale: Float get() = 0.92f
    val modalExitScale: Float get() = 0.96f

    /** 띄우는 화면(운행 결과)이 아래에서 떠오를 때 시작 배율. */
    val presentScale: Float get() = 0.96f

    /** 같은 자리 교체에서 들어오는 쪽 시작 배율. */
    val swapScale: Float get() = 0.98f

    /**
     * 같은 자리 교체(fade-through): 나가는 쪽은 아주 빨리 사라지고(`swapOut`, 응답 ≈ 0.15 s) 들어오는 쪽은 90 ms 뒤에
     * `smooth`로 떠오른다(`swapIn`) — 옛 내용과 새 내용이 겹쳐 읽히지 않는다(프레임 검토 2026-11-10: 겹치면 412.6과 0.0이 포개져 보였다).
     */
    fun <T> swapOut(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (isStatic) snap() else spring(dampingRatio = 1f, stiffness = SWAP_OUT_STIFFNESS, visibilityThreshold)

    fun <T> swapIn(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (isStatic) snap() else DelayedSpec(SWAP_DELAY_MS, smooth(visibilityThreshold))

    fun <T> snappy(): FiniteAnimationSpec<T> = if (isStatic) snap() else spring(dampingRatio = 0.85f, stiffness = 700f)

    val pressScale: Float get() = if (isStatic) 1f else 0.97f

    /** 누르는 동안 살짝 옅어진다(iOS·토스처럼 "눌렸다"가 바로 보인다). 면 모양과 상관없이 같은 방식이라 둥근 모서리 밖으로 번지지 않는다. */
    val pressAlpha: Float get() = if (isStatic) 1f else 0.85f

    /** 흐름 표시(경로 화살표) 한 바퀴 시간. 급하지 않게 — 숨 쉬는 속도보다 조금 빠른 정도. */
    val flowPeriodMs: Int get() = 1_400

    // 완료 표시(SuccessCheck, 디자인 언어 8.2 `motion.success`) — 한 번만 도는 축하 모션, 전체 약 1초.
    // 정지형이면 SuccessCheck가 이 값들을 쓰지 않고 완성된 모양을 바로 그린다.

    /** 원 등장: 덜 감쇠된 스프링 — 한 번 살짝 넘쳤다 자리 잡는다. */
    fun <T> successCircle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 320f)

    /** 원이 자리 잡을 즈음 한 번 퍼지는 테두리. */
    val successRingDelayMs: Int get() = 160

    fun <T> successRing(): FiniteAnimationSpec<T> = tween(durationMillis = 640, easing = LinearOutSlowInEasing)

    /** 체크 긋기 시작(테두리 시작 뒤) · 긋는 시간 — 처음은 빠르게, 끝은 천천히 내려놓듯. */
    val successStrokeDelayMs: Int get() = 120

    fun <T> successStroke(): FiniteAnimationSpec<T> =
        tween(durationMillis = 420, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f))

    /** 다 그린 뒤 톡: 빠르게 커졌다가 스프링으로 돌아온다. */
    fun <T> successPopOut(): FiniteAnimationSpec<T> = tween(durationMillis = 90, easing = FastOutSlowInEasing)

    fun <T> successPopBack(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 600f)

    companion object {
        private const val SMOOTH_STIFFNESS = 320f
        private const val QUICK_STIFFNESS = 700f
        private const val BOUNCY_DAMPING = 0.55f
        private const val BOUNCY_STIFFNESS = 600f
        private const val SWAP_OUT_STIFFNESS = 1_600f
        private const val SWAP_DELAY_MS = 90

        val Standard = MotionTokens(isStatic = false, ambient = true)
        val Static = MotionTokens(isStatic = true, ambient = true)

        /** 반복 모션까지 모두 멈춤 — 시스템 "애니메이션 줄이기", 스크린샷. */
        val Still = MotionTokens(isStatic = true, ambient = false)
    }
}
