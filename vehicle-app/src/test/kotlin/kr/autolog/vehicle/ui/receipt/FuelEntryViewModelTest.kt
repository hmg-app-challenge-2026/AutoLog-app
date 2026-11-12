package kr.autolog.vehicle.ui.receipt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FuelEntryViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.open(receiptId: String?): Pair<TestAppGraph, FuelEntryViewModel> {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.FuelEntry(receiptId))
        return g to FuelEntryViewModel(receiptId, g.receipts, g.session, g.navigator)
    }

    @Test
    fun `영수증으로 열면 OCR 값이 채워지고 추가하면 확정되어 inbox에서 빠진다`() = runTest {
        val (g, vm) = open("r-1")
        val state = vm.uiState.first { !it.loading }
        assertEquals("68000", state[FuelField.AMOUNT])

        vm.onAction(FuelEntryAction.SelectTrip("t-1"))
        vm.onAction(FuelEntryAction.Add)

        assertEquals(Route.Home, g.navigator.current)
        assertTrue(g.receipts.inbox.first().none { it.receiptId == "r-1" })
    }

    @Test
    fun `직접 입력 — 숫자판으로 금액을 넣고 추가하면 메인 화면으로`() = runTest {
        val (g, vm) = open(null)

        vm.onAction(FuelEntryAction.Edit(FuelField.AMOUNT))
        "50000".forEach { vm.onAction(FuelEntryAction.Key(it)) }
        vm.onAction(FuelEntryAction.CloseEditor)
        assertEquals("50000", vm.uiState.value[FuelField.AMOUNT])
        vm.onAction(FuelEntryAction.Add)

        assertEquals(Route.Home, g.navigator.current)
    }

    @Test
    fun `금액이 없으면 추가를 눌러도 저장하지 않는다`() = runTest {
        val (g, vm) = open(null)

        vm.onAction(FuelEntryAction.Add)

        assertEquals(Route.FuelEntry(null), g.navigator.current)
    }

    @Test
    fun `없는 영수증이면 이유를 보여 준다`() = runTest {
        val (_, vm) = open("r-none")

        assertEquals(FuelEntryError.ReceiptMissing, vm.uiState.first { !it.loading }.error)
    }
}
