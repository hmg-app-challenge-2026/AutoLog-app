package kr.autolog.vehicle.ui.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.currentMonth
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.nav.TripResultMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Home → [운행 기록 조회]로 들어온 상태. 시계는 데모 운행들이 이번 달에 들도록 실제 시각에 가깝게. */
    private fun TestScope.open(): Pair<TestAppGraph, HistoryViewModel> {
        val base = System.currentTimeMillis()
        val g = TestAppGraph(backgroundScope, AppClock { base + testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.History)
        val vm = HistoryViewModel(g.trips, g.profile, g.session, g.navigator, g.clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return g to vm
    }

    @Test
    fun `줄을 누르면 사후 확인 모드로 운행 결과를 연다 — 카운트다운 없음`() = runTest {
        val (g, vm) = open()

        vm.onAction(HistoryAction.Open("t-s2"))

        assertEquals(Route.TripResult("t-s2", TripResultMode.REVIEW), g.navigator.current)
    }

    @Test
    fun `지금 달에서 다음 달은 무시하고 이전 달로는 간다`() = runTest {
        val (g, vm) = open()
        val current = g.clock.currentMonth()

        vm.onAction(HistoryAction.NextMonth)
        assertEquals(current, vm.uiState.first { !it.loading }.month)
        vm.onAction(HistoryAction.PrevMonth)

        assertEquals(current.minusMonths(1), vm.uiState.first { it.month != current }.month)
    }

    @Test
    fun `운전자 미확정 줄에서 후보를 고르면 그 운행의 운전자가 정해진다`() = runTest {
        val (g, vm) = open()

        vm.onAction(HistoryAction.AssignDriver("t-s4", "u-2"))

        assertEquals("u-2", g.store.trip("t-s4")!!.driverUserId)
    }

    @Test
    fun `운행기록부 버튼은 운행기록부를 연다`() = runTest {
        val (g, vm) = open()

        vm.onAction(HistoryAction.OpenReport)

        assertEquals(Route.Report, g.navigator.current)
    }
}
