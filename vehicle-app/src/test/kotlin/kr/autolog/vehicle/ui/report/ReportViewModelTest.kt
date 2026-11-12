package kr.autolog.vehicle.ui.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.MailError
import kr.autolog.vehicle.core.MailResult
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Home → 운행 기록 조회 → [운행기록부 내보내기]. 데모 운행이 이번 달에 들도록 실제 시각에 가까운 시계. */
    private fun TestScope.open(): Pair<TestAppGraph, ReportViewModel> {
        val base = System.currentTimeMillis()
        val g = TestAppGraph(backgroundScope, AppClock { base + testScheduler.currentTime }, start = Route.Home)
        g.navigator.push(Route.History)
        g.navigator.push(Route.Report)
        val vm = ReportViewModel(g.reports, g.trips, g.settings, g.navigator, g.clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return g to vm
    }

    @Test
    fun `들어와서 두 번 누르면 보낸다 — 만들기 다음 보내기 · 2명`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { it.summary != null }

        vm.onAction(ReportAction.Primary)
        assertEquals(ReportStep.Built, vm.uiState.first { it.step == ReportStep.Built }.step)
        vm.onAction(ReportAction.Primary)
        advanceUntilIdle()

        assertEquals(ReportStep.Sent(2), vm.uiState.value.step)
        assertEquals(2, g.sources.mail.sent.single().second.size)
    }

    @Test
    fun `받는 사람을 끄면 그 사람은 빼고 보낸다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { it.recipients.size == 2 }

        vm.onAction(ReportAction.ToggleRecipient("me@example.com"))
        vm.onAction(ReportAction.Primary)
        vm.onAction(ReportAction.Primary)
        advanceUntilIdle()

        assertEquals(listOf("accounting@example.com"), g.sources.mail.sent.single().second)
    }

    @Test
    fun `보내기에 실패하면 이유를 보여 주고 다시 보내기로 다시 시도한다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { it.summary != null }
        g.sources.mail.nextResult = MailResult.Failed(MailError.AUTH, "인증 실패")

        vm.onAction(ReportAction.Primary)
        vm.onAction(ReportAction.Primary)
        advanceUntilIdle()
        assertEquals(ReportStep.SendFailed(MailError.AUTH), vm.uiState.value.step)

        g.sources.mail.nextResult = MailResult.Ok
        vm.onAction(ReportAction.Primary)
        advanceUntilIdle()
        assertEquals(ReportStep.Sent(2), vm.uiState.value.step)
    }

    @Test
    fun `달을 바꾸면 만든 파일을 버리고 만들기부터`() = runTest {
        val (_, vm) = open()
        vm.uiState.first { it.summary != null }
        vm.onAction(ReportAction.Primary)
        vm.uiState.first { it.step == ReportStep.Built }

        vm.onAction(ReportAction.PrevMonth)

        assertEquals(ReportStep.Ready, vm.uiState.first { it.step == ReportStep.Ready }.step)
    }

    @Test
    fun `보낸 뒤 완료를 누르면 운행 기록 조회로 돌아간다`() = runTest {
        val (g, vm) = open()
        vm.uiState.first { it.summary != null }
        vm.onAction(ReportAction.Primary)
        vm.onAction(ReportAction.Primary)
        advanceUntilIdle()

        vm.onAction(ReportAction.Primary)

        assertEquals(Route.History, g.navigator.current)
    }
}
