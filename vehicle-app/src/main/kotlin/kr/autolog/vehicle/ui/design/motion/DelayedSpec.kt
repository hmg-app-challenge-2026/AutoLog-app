package kr.autolog.vehicle.ui.design.motion

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec

/**
 * [spec]을 [delayMillis]만큼 늦게 시작한다 — 스프링에는 지연 인자가 없어서 만든다(같은 자리 교체에서 옛 내용이 먼저 사라진 뒤
 * 새 내용이 떠오르게, Material fade-through와 같은 순서). 지연 동안은 시작 값에 머문다.
 */
class DelayedSpec<T>(private val delayMillis: Int, private val spec: FiniteAnimationSpec<T>) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<T, V>): VectorizedFiniteAnimationSpec<V> =
        DelayedVectorized(delayMillis * NANOS_PER_MILLI, spec.vectorize(converter))
}

private class DelayedVectorized<V : AnimationVector>(
    private val delayNanos: Long,
    private val inner: VectorizedFiniteAnimationSpec<V>,
) : VectorizedFiniteAnimationSpec<V> {
    override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
        if (playTimeNanos < delayNanos) {
            initialValue
        } else {
            inner.getValueFromNanos(playTimeNanos - delayNanos, initialValue, targetValue, initialVelocity)
        }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
        if (playTimeNanos < delayNanos) {
            initialVelocity
        } else {
            inner.getVelocityFromNanos(playTimeNanos - delayNanos, initialValue, targetValue, initialVelocity)
        }

    override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
        delayNanos + inner.getDurationNanos(initialValue, targetValue, initialVelocity)
}

private const val NANOS_PER_MILLI = 1_000_000L
