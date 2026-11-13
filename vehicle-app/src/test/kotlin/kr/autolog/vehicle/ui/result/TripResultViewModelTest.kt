package kr.autolog.vehicle.ui.result

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.TripRow
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.nav.TripResultMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripResultViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** 운행 기록 조회 → 지난 운행([tripId])을 사후 확인으로 연 상태(D-29). */
    private fun TestScope.review(tripId: String): Pair<TestAppGraph, TripResultViewModel> {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.History)
        g.navigator.push(Route.TripResult(tripId, TripResultMode.REVIEW))
        return g to TripResultViewModel(tripId, g.tripResults, g.session, g.navigator, g.clock, TripResultMode.REVIEW)
    }

    @Test
    fun `사후 확인 — 카운트다운이 없어 기다려도 그대로, 왼쪽 위 뒤로는 저장하지 않고 목록으로`() = runTest {
        val (g, vm) = review("t-s2") // 이미 분류된 운행(지점 이동)
        val state = vm.uiState.first { !it.loading }
        assertNull(state.autoCloseSec)
        assertTrue(state.review)
        val before = purposeOf(g, "t-s2")

        advanceTimeBy(30_000)
        assertEquals(Route.TripResult("t-s2", TripResultMode.REVIEW), g.navigator.current)
        vm.onAction(TripResultAction.Close)

        assertEquals(Route.History, g.navigator.current)
        assertEquals(before, purposeOf(g, "t-s2"))
    }

    @Test
    fun `사후 확인 — 목적 미입력 운행은 수정 모드로 열리고 저장하면 목록으로`() = runTest {
        val (g, vm) = review("t-s1")
        assertTrue(vm.uiState.first { !it.loading }.editing)

        vm.onAction(TripResultAction.Select(PurposeCategory.COMMUTE))
        vm.onAction(TripResultAction.Save)

        acknowledgeSaved(g, vm)
        assertEquals(Route.History, g.navigator.current)
        assertEquals(PurposeCategory.COMMUTE, purposeOf(g)!!.category)
    }

    @Test
    fun `사후 확인 — 지난 운행 삭제하기는 보기 모드에서도 팝업, 확인이면 지우고 목록으로`() = runTest {
        val (g, vm) = review("t-s2")
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Delete)
        assertTrue(vm.uiState.first { it.confirmingDelete }.review)
        vm.onAction(TripResultAction.ConfirmDelete)
        runCurrent()

        assertEquals(Route.History, g.navigator.current)
        assertNull(g.store.trip("t-s2"))
    }

    @Test
    fun `사후 확인 — 목적 미입력 운행을 뒤로 나가면 미입력 그대로`() = runTest {
        val (g, vm) = review("t-s1")
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Close)

        assertEquals(Route.History, g.navigator.current)
        assertNull(purposeOf(g))
    }

    /** Home → 운행 결과(t-s1: 목적 없는 운행)로 들어온 상태. */
    private fun TestScope.open(tripId: String = "t-s1"): Pair<TestAppGraph, TripResultViewModel> {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.TripResult(tripId))
        return g to TripResultViewModel(tripId, g.tripResults, g.session, g.navigator, g.clock)
    }

    private suspend fun TestScope.purposeOf(g: TestAppGraph, tripId: String = "t-s1") = g.store.trip(tripId)!!.purpose

    @Test
    fun `아무것도 안 누르면 5초 뒤 일반 업무로 저장하고 메인 화면으로`() = runTest {
        val (g, vm) = open()
        assertEquals(5, vm.uiState.first { !it.loading }.autoCloseSec)

        advanceTimeBy(4_900)
        assertEquals(Route.TripResult("t-s1"), g.navigator.current)
        assertNull(purposeOf(g))
        advanceTimeBy(200)

        acknowledgeSaved(g, vm)
        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.BUSINESS, purposeOf(g)!!.category)
    }

    @Test
    fun `수정하기 — 카운트다운이 멈추고 목적을 바꿔 저장하기를 누르면 저장하고 메인 화면으로`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Edit)
        advanceTimeBy(10_000)
        assertEquals(Route.TripResult("t-s1"), g.navigator.current) // 멈춰 있다
        vm.onAction(TripResultAction.Select(PurposeCategory.NON_BUSINESS))
        vm.onAction(TripResultAction.Save)
        runCurrent()

        acknowledgeSaved(g, vm)
        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.NON_BUSINESS, purposeOf(g)!!.category)
    }

    @Test
    fun `수정하던 중 출발하면 수정을 접고 고른 목적으로 5초 뒤 저장한다 — 누를 수 없는 화면에 멈추지 않게`() = runTest {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.TripResult("t-s1"))
        val parked = MutableStateFlow(true)
        val vm = TripResultViewModel("t-s1", g.tripResults, g.session, g.navigator, g.clock, parked = parked)
        vm.uiState.first { !it.loading }
        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.Select(PurposeCategory.COMMUTE))

        parked.value = false
        runCurrent()
        val state = vm.uiState.first { !it.editing }
        assertEquals(5, state.autoCloseSec)
        advanceTimeBy(5_100)
        assertTrue(vm.uiState.value.saved) // 저장 완료 화면 — 정차·주행 중엔 저장한 때부터 1초
        advanceTimeBy(SAVED_HOLD_MS)

        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.COMMUTE, purposeOf(g)!!.category)
    }

    @Test
    fun `정차·주행 중엔 체크를 그렸다는 신호 없이도 저장 1초 뒤 닫힌다 — 앱이 뒤에 있어 화면이 안 그려져도`() = runTest {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.TripResult("t-s1"))
        val vm =
            TripResultViewModel(
                "t-s1",
                g.tripResults,
                g.session,
                g.navigator,
                g.clock,
                parked = MutableStateFlow(false),
            )
        vm.uiState.first { !it.loading }

        advanceTimeBy(AUTO_CLOSE_MS + 100)
        assertTrue(vm.uiState.value.saved)
        advanceTimeBy(SAVED_HOLD_MS + 100)

        assertEquals(listOf(Route.Home), g.navigator.backStack.value)
        assertEquals(PurposeCategory.BUSINESS, purposeOf(g)!!.category)
    }

    @Test
    fun `수정 모드 삭제하기 — 팝업을 열고, 취소면 그대로, 확인이면 운행을 지우고 메인 화면으로`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }
        vm.onAction(TripResultAction.Delete)
        assertFalse(vm.uiState.value.confirmingDelete) // 보기 모드에선 삭제 버튼이 없다

        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.Delete)
        assertTrue(vm.uiState.first { it.confirmingDelete }.editing)
        vm.onAction(TripResultAction.CancelDelete)
        assertFalse(vm.uiState.first { !it.confirmingDelete }.confirmingDelete)
        vm.onAction(TripResultAction.Delete)
        vm.onAction(TripResultAction.ConfirmDelete)
        runCurrent()

        assertEquals(Route.Home, g.navigator.current)
        assertNull(g.store.trip("t-s1")) // 목록에서 빠졌다(앱 A는 DISCARDED)
        assertFalse(vm.uiState.value.saved) // 저장 완료 화면 없이
    }

    @Test
    fun `종료 대기 중 삭제 — 앱 A가 끝낸 뒤 지우고, 목적은 저장하지 않는다`() = runTest {
        val (g, vm, tripId) = ending()
        vm.uiState.first { !it.loading }
        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.Delete)

        vm.onAction(TripResultAction.ConfirmDelete)
        runCurrent()

        assertTrue(g.trip.state.value is SessionState.Idle)
        assertNull(g.store.trip(tripId))
        assertEquals(Route.Home, g.navigator.current)
    }

    @Test
    fun `보기 모드에선 목적을 못 바꾸고, 저장하기는 바로 저장하고 돌아간다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Select(PurposeCategory.COMMUTE))
        assertEquals(PurposeCategory.BUSINESS, vm.uiState.first().selected)
        vm.onAction(TripResultAction.Save)

        acknowledgeSaved(g, vm)
        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.BUSINESS, purposeOf(g)!!.category)
    }

    @Test
    fun `5초 마감 1ms 전엔 그대로, 마감이 지나면 저장하고 돌아간다 — 막대가 다 사라질 때`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }

        advanceTimeBy(4_999)
        assertEquals(Route.TripResult("t-s1"), g.navigator.current)
        advanceTimeBy(2)
        acknowledgeSaved(g, vm)
        assertEquals(Route.Home, g.navigator.current)
    }

    @Test
    fun `수정 중 저장하기 — 고친 목적으로 저장된다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.Select(PurposeCategory.COMMUTE))
        vm.onAction(TripResultAction.Save)

        assertEquals(PurposeCategory.COMMUTE, purposeOf(g)!!.category)
        acknowledgeSaved(g, vm)
        assertTrue(g.navigator.current == Route.Home)
    }

    /**
     * 저장 뒤엔 바로 떠나지 않고 저장 완료 화면 — 체크를 다 그렸다는 신호 뒤 1초가 지나야 들어온 화면으로 간다.
     * 저장 완료 화면이 보이는지, 1초가 되기 전엔 그대로인지까지 확인한다.
     */
    private suspend fun TestScope.acknowledgeSaved(g: TestAppGraph, vm: TripResultViewModel) {
        runCurrent()
        val here = g.navigator.current
        vm.uiState.first { it.saved } // 저장 완료 화면
        assertTrue(here is Route.TripResult)
        vm.onAction(TripResultAction.CheckDrawn)
        advanceTimeBy(SAVED_HOLD_MS - 1)
        assertEquals(here, g.navigator.current)
        advanceTimeBy(2)
    }

    @Test
    fun `저장 완료 화면 — 체크를 다 그리기 전엔 기다려도 닫히지 않고, 확인은 바로 닫는다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }
        vm.onAction(TripResultAction.Save)
        runCurrent()
        assertNull(vm.uiState.first { it.saved }.autoCloseSec) // 저장 완료 화면엔 남은 시간이 없다

        advanceTimeBy(10_000) // 화면이 뜬 때부터 세지 않는다
        assertEquals(Route.TripResult("t-s1"), g.navigator.current)
        vm.onAction(TripResultAction.Close)

        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.BUSINESS, purposeOf(g)!!.category)
    }

    @Test
    fun `종료 대기 — 기록 이어가기로 떠나면 저장 완료 화면은 없다`() = runTest {
        val (_, vm) = ending()
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Continue)
        runCurrent()

        assertTrue(!vm.uiState.first().saved)
    }

    @Test
    fun `종료 대기 중 기어 P를 빼고 출발하면 저장하지 않고 닫힌다 — 진행 중인 운행에 목적을 저장하지 않는다`() = runTest {
        val (g, vm, tripId) = ending()
        vm.uiState.first { !it.loading }
        // 실제 저장소(Room)엔 기록 중인 운행 행이 있다(끝난 시각 없음) — 가짜 저장소에 같은 행을 둔다
        g.store.upsert(TripRow(tripId, 0L, endTime = null, null, "본사", null, null, purpose = null))

        g.trip.resume() // 앱 A: 기어가 P에서 빠졌다 → 종료 취소, 다시 기록 중
        runCurrent()

        assertEquals(Route.Home, g.navigator.current)
        assertTrue(!vm.uiState.value.saved)
        assertNull(g.store.trip(tripId)!!.purpose)
    }

    @Test
    fun `거리 수정 — 지금 거리로 팝업이 열리고, 고쳐 완료하면 화면에 수정됨, 저장하면 목적과 함께 저장된다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }
        vm.onAction(TripResultAction.Edit)

        vm.onAction(TripResultAction.EditDistance)
        assertEquals("23.4", vm.uiState.first { it.distanceEditor != null }.distanceEditor!!.draft)
        repeat(4) { vm.onAction(TripResultAction.DistanceDelete) }
        "25.5".forEach { vm.onAction(TripResultAction.DistanceKey(it)) }
        vm.onAction(TripResultAction.ConfirmDistance)

        val state = vm.uiState.first { it.distanceEditor == null }
        assertEquals("25.5", state.km)
        assertTrue(state.distanceEdited)
        assertFalse(g.store.trip("t-s1")!!.isDistanceEdited) // 아직 저장 전
        vm.onAction(TripResultAction.Save)
        runCurrent()

        acknowledgeSaved(g, vm)
        val row = g.store.trip("t-s1")!!
        assertEquals(25.5, row.distanceKm!!, 0.0)
        assertTrue(row.isDistanceEdited)
        assertEquals(PurposeCategory.BUSINESS, row.purpose!!.category)
    }

    @Test
    fun `거리 수정 — 0이나 1500 km 넘는 값은 이유를 보여 주고 팝업이 남는다, X는 고치지 않고 닫는다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { !it.loading }
        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.EditDistance)
        repeat(4) { vm.onAction(TripResultAction.DistanceDelete) }

        vm.onAction(TripResultAction.ConfirmDistance)
        assertEquals(DistanceEntryError.EMPTY_OR_ZERO, vm.uiState.first().distanceEditor!!.error)
        "1501".forEach { vm.onAction(TripResultAction.DistanceKey(it)) }
        vm.onAction(TripResultAction.ConfirmDistance)
        assertEquals(DistanceEntryError.TOO_LONG, vm.uiState.first().distanceEditor!!.error)
        vm.onAction(TripResultAction.CancelDistance)

        val state = vm.uiState.first { it.distanceEditor == null }
        assertEquals("23.4", state.km)
        assertFalse(state.distanceEdited)
        assertFalse(g.store.trip("t-s1")!!.isDistanceEdited)
    }

    @Test
    fun `거리 수정은 수정 모드에서만 열리고, 출발하면 팝업이 닫힌다`() = runTest {
        val parked = MutableStateFlow(true)
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.TripResult("t-s1"))
        val vm = TripResultViewModel("t-s1", g.tripResults, g.session, g.navigator, g.clock, parked = parked)
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.EditDistance)
        assertNull(vm.uiState.first().distanceEditor)
        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.EditDistance)
        assertTrue(vm.uiState.first().distanceEditor != null)
        parked.value = false
        runCurrent()

        assertNull(vm.uiState.first().distanceEditor)
    }

    @Test
    fun `카드는 장소 이름과 주소를 함께 보여 준다`() = runTest {
        val (_, vm) = open()

        val state = vm.uiState.first { !it.loading }

        assertEquals("본사", state.origin)
        assertEquals("서울 서대문구 연희동 연희로 11-1", state.originAddress)
        assertEquals("경기 성남시 분당구 삼평동 판교역로 166", state.destinationAddress)
    }

    /** 기록 중 → 기어 P(종료 대기, 5초 뒤 종료) → Home 위 운행 결과 카드. */
    private fun TestScope.ending(): Triple<TestAppGraph, TripResultViewModel, String> {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        runCurrentStart(g)
        val tripId = (g.trip.state.value as SessionState.StopCandidate).trip.tripId
        g.navigator.push(Route.TripResult(tripId))
        return Triple(g, TripResultViewModel(tripId, g.tripResults, g.session, g.navigator, g.clock), tripId)
    }

    private fun TestScope.runCurrentStart(g: TestAppGraph) {
        backgroundScope.launch { g.trip.startSession() }
        runCurrent()
        g.trip.addKm(12.3)
        g.trip.setMotion(VehicleMotion.PARKED)
    }

    @Test
    fun `종료 대기 — 남은 시간은 앱 A 종료 시각, 기록 이어가기면 되돌리고 돌아간다`() = runTest {
        val (g, vm) = ending()
        val s = vm.uiState.first { !it.loading }
        assertTrue(s.canContinue)
        assertEquals(5, s.autoCloseSec)

        vm.onAction(TripResultAction.Continue)
        runCurrent()

        assertTrue(g.trip.state.value is SessionState.Recording)
        assertEquals(Route.Home, g.navigator.current)
    }

    @Test
    fun `종료 대기 — 아무것도 안 누르면 종료 시각에 끝나고 일반 업무로 저장해 돌아간다`() = runTest {
        val (g, vm, tripId) = ending()
        vm.uiState.first { !it.loading }

        advanceTimeBy(5_100)

        acknowledgeSaved(g, vm)
        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.BUSINESS, g.store.trip(tripId)!!.purpose!!.category)
    }

    @Test
    fun `종료 대기 — 저장하기는 바로 끝내고 저장`() = runTest {
        val (g, vm, tripId) = ending()
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Save)
        runCurrent()

        acknowledgeSaved(g, vm)
        assertEquals(Route.Home, g.navigator.current)
        assertEquals(PurposeCategory.BUSINESS, g.store.trip(tripId)!!.purpose!!.category)
    }

    @Test
    fun `종료 대기 중 수정하기 — 끝나도 카드에 남고 고른 목적으로 저장된다`() = runTest {
        val (g, vm, tripId) = ending()
        vm.uiState.first { !it.loading }

        vm.onAction(TripResultAction.Edit)
        vm.onAction(TripResultAction.Select(PurposeCategory.COMMUTE))
        advanceTimeBy(5_100) // 운행은 앱 A 시각에 끝난다

        assertEquals(Route.TripResult(tripId), g.navigator.current)
        assertEquals(PurposeCategory.COMMUTE, g.store.trip(tripId)!!.purpose!!.category)
        assertTrue(!vm.uiState.first().canContinue)
    }
}
