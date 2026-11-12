package kr.autolog.vehicle.data.repository

import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StopReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** 앱 A 계약(core) → 화면 타입 변환. 계약이 바뀌면 이 테스트와 변환 함수만 고친다(스펙 6.6). */
class SessionRepositoryTest {
    private val live = LiveTrip("t-1", startTime = 100L, 18_420.5, "판교", "u-1", originLabel = "본사")
    private val active = ActiveTrip("t-1", startTime = 100L, origin = "본사")

    @Test
    fun `세션 상태는 화면에 필요한 값만 남겨 옮긴다`() {
        assertEquals(TripSession.Idle, SessionState.Idle.toTripSession())
        assertEquals(TripSession.Recording(active, null), SessionState.Recording(live, null, 0).toTripSession())
        assertEquals(
            TripSession.AutoEnding(active, 23.4, AutoEndReason.NAVI_ARRIVED, deadlineAt = 2),
            SessionState.StopCandidate(live, 23.4, StopReason.NAVI_ARRIVED, since = 1, deadlineAt = 2).toTripSession(),
        )
        assertEquals(TripSession.PendingReview("t-1"), SessionState.PendingReview(live, 23.4, null).toTripSession())
        assertEquals(TripSession.NeedsRecovery(3.2), SessionState.NeedsRecovery(live, 3.2).toTripSession())
    }
}
