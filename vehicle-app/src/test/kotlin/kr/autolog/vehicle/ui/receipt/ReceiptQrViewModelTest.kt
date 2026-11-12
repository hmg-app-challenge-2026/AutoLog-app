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
import kr.autolog.vehicle.fake.FakeReceiptApi
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptQrViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Home → [주유 기록 추가]로 QR 화면에 들어온 상태. [emptyInbox]면 기다리는 영수증이 없다. */
    private fun TestScope.open(emptyInbox: Boolean): Pair<TestAppGraph, ReceiptQrViewModel> {
        val g = TestAppGraph(backgroundScope, AppClock { testScheduler.currentTime }, start = Route.Home)
        if (emptyInbox) g.store.ackReceipt(FakeReceiptApi.SAMPLE_RECEIPT.receiptId)
        g.navigator.push(Route.ReceiptQr)
        return g to ReceiptQrViewModel(g.receipts, g.navigator, g.clock)
    }

    @Test
    fun `기존 영수증이 있어도 먼저 QR 화면을 보여 준다`() = runTest {
        val (g, vm) = open(emptyInbox = false)

        assertEquals(listOf(Route.Home, Route.ReceiptQr), g.navigator.backStack.value)
        assertEquals(false, vm.uiState.value.loading)

        g.store.pushReceipt(FakeReceiptApi.SAMPLE_UNSURE)

        assertEquals(listOf(Route.Home, Route.FuelEntry("r-2")), g.navigator.backStack.value)
    }

    @Test
    fun `QR을 기다리다 영수증이 도착하면 QR 화면을 입력 창으로 바꾼다`() = runTest {
        val (g, vm) = open(emptyInbox = true)
        val state = vm.uiState.first { !it.loading }
        assertEquals(DEMO_QR_LIFETIME_MS / 1_000, state.remainingSec.toLong())

        g.store.pushReceipt(FakeReceiptApi.SAMPLE_UNSURE)

        assertEquals(listOf(Route.Home, Route.FuelEntry("r-2")), g.navigator.backStack.value)
    }

    @Test
    fun `직접 입력하기는 빈 입력 창을 QR 위에 연다`() = runTest {
        val (g, vm) = open(emptyInbox = true)

        vm.onAction(ReceiptQrAction.ManualEntry)

        assertEquals(Route.FuelEntry(), g.navigator.current)
    }
}
