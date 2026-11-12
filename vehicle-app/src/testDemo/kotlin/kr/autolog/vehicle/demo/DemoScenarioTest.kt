package kr.autolog.vehicle.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.data.fake.FakeTripSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoScenarioTest {
    @Test
    fun `주행 시나리오는 출발→신호 대기→출발→주차 후 5초 뒤 자동 종료`() = runTest {
        val clock = AppClock { testScheduler.currentTime }
        val trip = FakeTripSession(backgroundScope, clock)
        val demo = DemoScenario(trip, backgroundScope, clock)

        demo.playDrive()
        runCurrent()
        assertEquals(VehicleMotion.MOVING, trip.motion.value)
        advanceTimeBy(10_500)
        assertEquals(VehicleMotion.STOPPED, trip.motion.value)
        advanceTimeBy(15_500)
        assertEquals(VehicleMotion.PARKED, trip.motion.value)
        advanceTimeBy(5_100)
        runCurrent()

        val review = trip.state.value as SessionState.PendingReview
        assertEquals(9.0, review.distanceKm!!, 0.001) // 10 × 0.4 + 10 × 0.5
        assertTrue(trip.totalOdometerKm.value!! > 18_420.5)
    }
}
