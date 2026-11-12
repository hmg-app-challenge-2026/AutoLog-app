package kr.autolog.vehicle.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.MissedTrip
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.graph() = TestAppGraph(
        backgroundScope,
        clock = AppClock {
            testScheduler.currentTime
        },
        start = Route.Home,
    )

    @Test
    fun `기어 P(종료 대기)가 되면 운행 결과 카드가 바로 열리고, 끝나도 다시 열지 않는다`() = runTest {
        val g = graph()
        HomeViewModel(g.session, g.context, g.trips, g.navigator, g.clock)
        g.trip.startSession()
        advanceTimeBy(1_100)

        g.trip.setMotion(VehicleMotion.PARKED)
        advanceTimeBy(1_100)
        val tripId = (g.trip.state.value as SessionState.StopCandidate).trip.tripId
        assertEquals(listOf(Route.Home, Route.TripResult(tripId)), g.navigator.backStack.value)

        advanceTimeBy(6_000) // 종료 시각이 지나 끝남(PendingReview) — 같은 운행이라 카드를 또 쌓지 않는다
        assertEquals(listOf(Route.Home, Route.TripResult(tripId)), g.navigator.backStack.value)
    }

    @Test
    fun `계속 기록으로 되돌렸다가 다시 기어 P면 카드를 다시 연다`() = runTest {
        val g = graph()
        HomeViewModel(g.session, g.context, g.trips, g.navigator, g.clock)
        g.trip.startSession()
        g.trip.setMotion(VehicleMotion.PARKED)
        advanceTimeBy(1_100)
        g.navigator.pop()
        g.trip.resume()
        advanceTimeBy(1_100)

        g.trip.setMotion(VehicleMotion.PARKED)
        advanceTimeBy(1_100)

        assertTrue(g.navigator.current is Route.TripResult)
    }

    @Test
    fun `기록 종료를 누르면 자동 종료와 같은 5초 종료 대기에 들어가고 운행 결과가 열린다`() = runTest {
        val g = graph()
        val vm = HomeViewModel(g.session, g.context, g.trips, g.navigator, g.clock)
        g.trip.startSession()

        vm.onAction(HomeAction.EndNow)

        assertTrue(g.trip.state.value is SessionState.StopCandidate)
        advanceTimeBy(4_000)
        assertTrue(g.trip.state.value is SessionState.StopCandidate)
        assertTrue(g.navigator.current is Route.TripResult) // 종료 대기 동안 카운트다운과 함께 열려 있다
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(g.trip.state.value is SessionState.PendingReview)
    }

    @Test
    fun `운행이 끝나면 목적 확인 화면이 자동으로 열린다`() = runTest {
        val g = graph()
        HomeViewModel(g.session, g.context, g.trips, g.navigator, g.clock)
        g.trip.startSession()
        g.trip.addKm(2.0)

        g.trip.finalize()
        advanceTimeBy(1_100)

        val current = g.navigator.current
        assertTrue(current is Route.TripResult)
        assertEquals(
            (g.trip.state.value as SessionState.PendingReview).trip.tripId,
            (current as Route.TripResult).tripId,
        )
    }

    @Test
    fun `독의 운행 기록은 이력(기록부 포함)으로 간다`() = runTest {
        val g = graph()
        val vm = HomeViewModel(g.session, g.context, g.trips, g.navigator, g.clock)

        vm.onAction(HomeAction.OpenRecords)

        assertEquals(Route.History, g.navigator.current)
    }

    @Test
    fun `놓친 운행 팝업에서 추가하기를 누르면 운행으로 추가된다`() = runTest {
        val g = graph()
        val vm = HomeViewModel(g.session, g.context, g.trips, g.navigator, g.clock)
        g.trip.setMissedTrip(MissedTrip(15.0, 0))
        assertEquals("15.0", vm.uiState.first { it.missedKm != null }.missedKm)

        vm.onAction(HomeAction.AcceptMissedTrip)

        assertNull(g.trip.missedTrip.value)
    }
}
