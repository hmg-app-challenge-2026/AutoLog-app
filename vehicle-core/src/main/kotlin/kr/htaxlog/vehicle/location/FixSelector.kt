package kr.htaxlog.vehicle.location

import kr.htaxlog.vehicle.core.LocationFix

/**
 * 출발·도착 스냅샷에 쓸 좌표를 고른다. 순수 로직이라 JVM 테스트가 된다.
 *
 * - 최신 fix가 [maxAgeMs] 이내면 그것을 쓴다.
 * - 너무 오래됐으면 쓰지 않는다(null). 오래된 좌표로 장소를 잘못 매칭하는 것보다 폴백이 낫다.
 */
object FixSelector {
    const val DEFAULT_MAX_AGE_MS = 30_000L

    fun freshOrNull(latest: LocationFix?, nowMs: Long, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): LocationFix? =
        latest?.takeIf { nowMs - it.timestampMs in 0..maxAgeMs }
}
