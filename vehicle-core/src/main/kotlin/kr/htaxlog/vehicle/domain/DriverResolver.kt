package kr.htaxlog.vehicle.domain

import kr.htaxlog.vehicle.core.DriverCandidate
import kr.htaxlog.vehicle.core.DriverSource
import kr.htaxlog.vehicle.core.ScheduleInfo
import kr.htaxlog.vehicle.core.TripFacts
import kr.htaxlog.vehicle.core.TripSummary
import kr.htaxlog.vehicle.core.UserInfo
import kotlin.math.abs

/**
 * 운전자 확정 (F-15, F-20, 기술명세 3.3). 첫 번째로 맞는 규칙을 쓴다.
 * ① 사용자가 고름 1.0 ② 이 차 배차 ±1시간에 운전자가 딱 1명 0.95 ③ 2시간 안에 끝난 직전 운행의 운전자 0.60(후보만)
 * ④ 없으면 null — 미확정으로 두고 다음 운전자 선택 때 묻는다.
 */
object DriverResolver {
    const val SCHEDULE_WINDOW_MS = 60 * 60 * 1000L
    const val PREVIOUS_TRIP_WINDOW_MS = 2 * 60 * 60 * 1000L

    fun resolve(
        trip: TripFacts,
        schedules: List<ScheduleInfo>,
        previousTrip: TripSummary?,
        users: List<UserInfo>,
    ): DriverCandidate? {
        val names = users.associate { it.userId to it.name }
        fun candidate(userId: String, source: DriverSource, confidence: Double) =
            DriverCandidate(userId, names[userId] ?: userId, source, confidence)

        trip.driverUserId?.let { return candidate(it, DriverSource.DRIVER_SELECTED, 1.0) }

        val scheduled =
            schedules
                .filter { s ->
                    trip.startTime in s.startsAt..s.endsAt ||
                        abs(s.startsAt - trip.startTime) <= SCHEDULE_WINDOW_MS
                }
                .map { it.userId }
                .distinct()
        if (scheduled.size == 1) return candidate(scheduled.single(), DriverSource.SCHEDULE, 0.95)

        val prev = previousTrip?.takeIf { it.userId != null && it.startTime < trip.startTime }
        val prevEnd = prev?.endTime ?: prev?.startTime
        if (prev != null && prevEnd != null && trip.startTime - prevEnd in 0..PREVIOUS_TRIP_WINDOW_MS) {
            return candidate(prev.userId!!, DriverSource.PREVIOUS_TRIP, 0.60)
        }
        return null
    }

    /** [trips] 중 [before] 전에 시작한 마지막 운행. */
    fun previousTrip(trips: List<TripSummary>, before: Long): TripSummary? =
        trips.filter { it.startTime < before }.maxByOrNull { it.startTime }
}
