package kr.autolog.vehicle.ui.settings

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
import kr.autolog.vehicle.data.repository.OverlayPermissionRepository
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.overlay.RecordingOverlay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.open(
        overlay: OverlayPermissionRepository = RecordingOverlay.None,
        relayUrl: String = "https://relay.example",
    ): Pair<TestAppGraph, SettingsViewModel> {
        val g =
            TestAppGraph(
                backgroundScope,
                AppClock {
                    testScheduler.currentTime
                },
                start = Route.Home,
                relayUrl = relayUrl,
            )
        g.navigator.push(Route.Settings)
        val vm = SettingsViewModel(g.settings, g.navigator, g.clock, AppInfo("0.1.0", demo = true), overlay)
        backgroundScope.launch { vm.uiState.collect {} }
        return g to vm
    }

    @Test
    fun `연결 코드 — 6자리만 넣고 연결하면 빌드에 고정된 서버로 연결된다`() = runTest {
        val (_, vm) = open()
        vm.onAction(SettingsAction.StartRegister)
        vm.onAction(SettingsAction.EditDialog(DialogField.CONNECT_CODE, "7kq3mzW")) // 소문자는 대문자로, 7번째는 무시

        val dialog = vm.uiState.first { (it.dialog as? SettingsDialog.Register)?.code?.length == 6 }.dialog
        assertEquals("7KQ3MZ", (dialog as SettingsDialog.Register).code)
        vm.onAction(SettingsAction.SubmitDialog)
        advanceUntilIdle()

        assertNull(vm.uiState.value.dialog)
        assertEquals(TaskResult.Done, vm.uiState.value.results[SettingsTask.REGISTER])
        assertEquals(
            "https://relay.example",
            (
                vm.uiState.first {
                    it.link is LinkUi.Linked
                }.link as LinkUi.Linked
                ).serverUrl,
        )
    }

    @Test
    fun `연결 코드 — 서버가 쓰지 않는 0·1·I·O와 기호는 들어가지 않는다`() = runTest {
        val (_, vm) = open()
        vm.onAction(SettingsAction.StartRegister)
        vm.onAction(SettingsAction.EditDialog(DialogField.CONNECT_CODE, "01IO-#A2"))

        val dialog = vm.uiState.first { (it.dialog as? SettingsDialog.Register)?.code?.length == 2 }.dialog
        assertEquals("A2", (dialog as SettingsDialog.Register).code)
    }

    @Test
    fun `연결 실패면 팝업에 이유를 남긴다`() = runTest {
        val (_, vm) = open(relayUrl = "ftp://x") // 빌드 설정이 잘못된 경우
        vm.onAction(SettingsAction.StartRegister)
        vm.onAction(SettingsAction.EditDialog(DialogField.CONNECT_CODE, "7KQ3MZ"))
        vm.onAction(SettingsAction.SubmitDialog)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dialog is SettingsDialog.Register)
        assertTrue(vm.uiState.value.results[SettingsTask.REGISTER] is TaskResult.Failed)
    }

    @Test
    fun `지금 전송하면 전송 대기가 0건`() = runTest {
        val (_, vm) = open()
        assertEquals(2, vm.uiState.first { it.upload.pending > 0 }.upload.pending)

        vm.onAction(SettingsAction.UploadNow)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.upload.pending)
        assertEquals(TaskResult.Done, vm.uiState.value.results[SettingsTask.UPLOAD])
    }

    @Test
    fun `받는 사람 추가와 지우기 — 회사 지정은 지울 수 없다`() = runTest {
        val (_, vm) = open()
        vm.onAction(SettingsAction.StartAddRecipient)
        vm.onAction(SettingsAction.EditDialog(DialogField.EMAIL, "boss@example.com"))
        vm.onAction(SettingsAction.SubmitDialog)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.recipients.any { it.email == "boss@example.com" })

        vm.onAction(SettingsAction.AskRemoveRecipient("boss@example.com"))
        vm.onAction(SettingsAction.SubmitDialog)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.recipients.none { it.email == "boss@example.com" })

        vm.onAction(SettingsAction.AskRemoveRecipient("accounting@example.com"))
        vm.onAction(SettingsAction.SubmitDialog)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.results[SettingsTask.REMOVE_RECIPIENT] is TaskResult.Failed)
    }

    @Test
    fun `직원 추가`() = runTest {
        val (_, vm) = open()
        vm.onAction(SettingsAction.StartAddStaff)
        vm.onAction(SettingsAction.EditDialog(DialogField.NAME, "최신입"))
        vm.onAction(SettingsAction.EditDialog(DialogField.DEPT, "영업2팀"))
        vm.onAction(SettingsAction.SubmitDialog)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.staff.any { it.name == "최신입" && it.dept == "영업2팀" && !it.fromServer })
    }

    @Test
    fun `메일 계정 — 칸을 채우고 연결 확인하면 연결됨, 비밀번호 칸은 비운다`() = runTest {
        val (_, vm) = open()
        vm.onAction(SettingsAction.EditMail(MailField.HOST, "smtp.example.com"))
        vm.onAction(SettingsAction.EditMail(MailField.USERNAME, "me@example.com"))
        vm.onAction(SettingsAction.EditMail(MailField.PASSWORD, "app-pw"))
        vm.onAction(SettingsAction.SaveMail)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mailConnected)
        assertEquals("", vm.uiState.value.mail.password)
    }

    @Test
    fun `월말 자동 발송 스위치는 저장소에 남는다`() = runTest {
        val (g, vm) = open()

        vm.onAction(SettingsAction.SetAutoMonthlySend(true))

        assertTrue(g.settings.autoMonthlySend.value)
        assertTrue(vm.uiState.first { it.autoMonthlySend }.autoMonthlySend)
    }

    @Test
    fun `섹션을 열고 닫고, 뒤로는 화면을 나간다`() = runTest {
        val (g, vm) = open()
        vm.onAction(SettingsAction.Open(SettingsSection.MAIL))
        assertEquals(SettingsSection.MAIL, vm.uiState.first { it.section != null }.section)
        vm.onAction(SettingsAction.CloseSection)
        assertNull(vm.uiState.first { it.section == null }.section)

        vm.onAction(SettingsAction.Back)

        assertEquals(Route.Home, g.navigator.current)
    }

    /** 시스템 권한 화면을 흉내 낸다: [screen]이 없으면 열지 못한다, 열면 사용자가 허용한 것으로. */
    private class FakeOverlayPermission(var allowed: Boolean, private val screen: Boolean) :
        OverlayPermissionRepository {
        override val supported = true

        override fun isAllowed() = allowed

        override fun openSettings(): Boolean = screen
    }

    @Test
    fun `데모 빌드에는 알약 권한 카드가 없다`() = runTest {
        val (_, vm) = open()

        assertNull(vm.uiState.value.overlay)
    }

    @Test
    fun `권한 화면에서 허용하고 돌아오면 다시 확인해 켜짐으로 바뀐다`() = runTest {
        val permission = FakeOverlayPermission(allowed = false, screen = true)
        val (_, vm) = open(permission)
        assertEquals(OverlayUi(allowed = false), vm.uiState.value.overlay)

        vm.onAction(SettingsAction.OpenOverlaySettings)
        permission.allowed = true
        vm.onAction(SettingsAction.RefreshOverlay)

        assertEquals(OverlayUi(allowed = true), vm.uiState.first { it.overlay?.allowed == true }.overlay)
    }

    @Test
    fun `이 차량에 권한 화면이 없으면 그 사실을 알린다`() = runTest {
        val (_, vm) = open(FakeOverlayPermission(allowed = false, screen = false))

        vm.onAction(SettingsAction.OpenOverlaySettings)

        assertEquals(
            OverlayUi(allowed = false, settingsUnavailable = true),
            vm.uiState.first { it.overlay?.settingsUnavailable == true }.overlay,
        )
    }
}
