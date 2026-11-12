package kr.autolog.vehicle.data.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.InputMode
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StopReason
import kr.autolog.vehicle.core.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTripSessionTest {
    private fun TestScope.session() = FakeTripSession(backgroundScope, AppClock { testScheduler.currentTime })

    @Test
    fun `기어 P면 5초 카운트다운을 시작하고 아무것도 안 누르면 기록이 끝난다`() = runTest {
        val s = session()
        s.startSession()
        s.addKm(23.4)

        s.setMotion(VehicleMotion.PARKED)

        val candidate = s.state.value as SessionState.StopCandidate
        assertEquals(StopReason.GEAR_P, candidate.reason)
        assertEquals(candidate.since + 5_000, candidate.deadlineAt)
        advanceTimeBy(5_001)
        runCurrent()
        val review = s.state.value as SessionState.PendingReview
        assertEquals(23.4, review.distanceKm!!, 0.001)
    }

    @Test
    fun `계속 기록을 누르면 자동 종료가 취소된다`() = runTest {
        val s = session()
        s.startSession()
        s.setMotion(VehicleMotion.PARKED)

        s.resume()
        advanceTimeBy(6_000)
        runCurrent()

        assertTrue(s.state.value is SessionState.Recording)
    }

    @Test
    fun `기록 종료를 누르면 기다리지 않고 바로 끝난다`() = runTest {
        val s = session()
        s.startSession()
        s.setMotion(VehicleMotion.PARKED)

        s.finalize()

        assertTrue(s.state.value is SessionState.PendingReview)
    }

    @Test
    fun `카운트다운 중 다시 출발하면 기록을 이어 간다`() = runTest {
        val s = session()
        s.startSession()
        s.setMotion(VehicleMotion.PARKED)

        s.setMotion(VehicleMotion.MOVING)
        advanceTimeBy(6_000)
        runCurrent()

        assertTrue(s.state.value is SessionState.Recording)
    }

    @Test
    fun `운행이 없을 때 출발하면 자동으로 기록을 시작한다`() = runTest {
        val s = session()

        s.setMotion(VehicleMotion.MOVING)
        runCurrent()

        assertTrue(s.state.value is SessionState.Recording)
    }

    @Test
    fun `거리를 더하면 누적거리와 이번 운행 거리가 함께 는다`() = runTest {
        val s = session()
        s.startSession()

        s.addKm(1.5)

        assertEquals(18_422.0, s.totalOdometerKm.value!!, 0.001)
        assertEquals(1.5, (s.state.value as SessionState.Recording).liveKm!!, 0.001)
    }

    @Test
    fun `운행이 끝나면 저장소에 목적 확인 필요로 남고 확정하면 빠진다`() = runTest {
        val store = FakeLocalStore(AppClock { testScheduler.currentTime }, seed = false)
        val s = FakeTripSession(backgroundScope, AppClock { testScheduler.currentTime }, store = store)
        s.startSession()
        s.addKm(3.0)
        s.finalize()
        val tripId = (s.state.value as SessionState.PendingReview).trip.tripId

        assertEquals(listOf(tripId), store.pendingReviews.first().map { it.tripId })
        s.confirmPurpose(tripId, PurposeDetail.MEETING, null, InputMode.TAP)

        assertTrue(store.pendingReviews.first().isEmpty())
        assertTrue(s.state.value is SessionState.Idle)
    }

    @Test
    fun `목적 확인을 미뤄도 다음 운행은 시작된다 — 미룬 운행은 목적 확인 필요로 남는다`() = runTest {
        val store = FakeLocalStore(AppClock { testScheduler.currentTime }, seed = false)
        val s = FakeTripSession(backgroundScope, AppClock { testScheduler.currentTime }, store = store)
        s.startSession()
        s.finalize()
        val deferred = (s.state.value as SessionState.PendingReview).trip.tripId
        advanceTimeBy(1_000)

        s.setMotion(VehicleMotion.MOVING) // 출발하면 자동으로 새 운행
        runCurrent()

        assertTrue(s.state.value is SessionState.Recording)
        assertEquals(listOf(deferred), store.pendingReviews.first().map { it.tripId })
    }
}
