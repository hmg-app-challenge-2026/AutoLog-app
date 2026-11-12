package kr.autolog.vehicle.ui.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.ui.result.AUTO_CLOSE_MS
import kr.autolog.vehicle.ui.result.SAVED_HOLD_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AutoLogRootTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Test
    fun `정차 중엔 주차 때와 같은 Home을 읽기 전용으로 보인다 — 기록 시작만 눌리고 운전자 배지는 없다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking { graph.session.selectDriver("u-1") }
        graph.trip.setMotion(VehicleMotion.STOPPED)

        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.waitForIdle()

        compose.onNodeWithText("대기 중").assertIsDisplayed() // 예전 정차 패널이 아니라 같은 Home(D-31)
        compose.onNodeWithText("주유 기록 추가").assertIsDisplayed() // 타일은 같은 자리에 흐리게
        compose.onNodeWithText("김영업님").assertDoesNotExist() // 배지는 주차한 대기 Home에만(D-30)
        assertEquals(1, compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
    }

    @Test
    fun `기록 종료 뒤 자동 시작이 멈춰 있어도 정차·주행 중 기록 시작으로 다시 기록한다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking { graph.trip.startSession() }
        graph.trip.setMotion(VehicleMotion.MOVING)
        runBlocking { graph.trip.finalize() } // 달리는 중 [기록 종료] — 다음 주차까지 자동 시작 멈춤(D-34)
        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.idleMain(AUTO_CLOSE_MS + SAVED_HOLD_MS + 1_100) // 버튼 없는 결과 카드가 저절로 저장·닫힘
        assertTrue(graph.trip.state.value is SessionState.Idle)

        compose.onNodeWithText("기록 시작").performClick()
        compose.idleMain(1_100)

        assertTrue(graph.trip.state.value is SessionState.Recording)
    }

    @Test
    fun `정차 중 기록 중이면 기록 종료만 눌리고, 누르면 바로 끝나 버튼 없는 결과 카드가 뜬다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking { graph.trip.startSession() }
        graph.trip.setMotion(VehicleMotion.STOPPED)

        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.mainClock.advanceTimeBy(1_100) // 세션 값은 1초 스로틀

        assertEquals(1, compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
        compose.onNodeWithText("기록 종료").performClick()
        compose.waitForIdle()

        assertTrue(graph.trip.state.value is SessionState.PendingReview) // 되돌릴 버튼이 없으니 기다리지 않는다
        compose.idleMain(1_100) // 세션 값은 1초 스로틀(ViewModel 쪽 시간은 메인 루퍼가 센다)
        assertTrue(graph.navigator.current is Route.TripResult) // 결과 카드는 바로(D-35)
        compose.onNodeWithText("주차하면 운행 기록을 수정할 수 있어요").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
    }

    @Test
    fun `주행 중에도 기록 종료만 눌리고, 누르면 기다리지 않고 바로 끝난다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking { graph.trip.startSession() }
        graph.trip.setMotion(VehicleMotion.MOVING)

        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.mainClock.advanceTimeBy(1_100)

        assertEquals(1, compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
        compose.onNodeWithText("기록 종료").performClick()
        compose.waitForIdle()

        assertTrue(graph.trip.state.value is SessionState.PendingReview)
        compose.idleMain(1_100)
        assertTrue(graph.navigator.current is Route.TripResult)
        compose.onNodeWithText("주차하면 운행 기록을 수정할 수 있어요").assertIsDisplayed()

        compose.idleMain(AUTO_CLOSE_MS + 100) // 5초 뒤 저절로 저장 → 체크(정지형이라 바로 다 그려짐)
        compose.onNodeWithText("운행 기록을 저장했어요").assertIsDisplayed()
        compose.onNodeWithText("확인").assertDoesNotExist()
        compose.idleMain(SAVED_HOLD_MS + 100) // 1초 뒤 Home
        compose.onNodeWithText("운행 기록을 저장했어요").assertDoesNotExist()
        assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
    }

    @Test
    fun `주행 중에도 빈 화면이 아니라 같은 Home을 그린 채 물러난다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking { graph.trip.startSession() }
        var backgrounded = false
        compose.setContent { AutoLogRoot(graph, onRequestBackground = { backgrounded = true }) }

        graph.trip.setMotion(VehicleMotion.MOVING)
        compose.mainClock.advanceTimeBy(1_100)

        assertTrue(backgrounded)
        compose.onNodeWithText("기록 중").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1024dp-h640dp")
    fun `기록 중 Home엔 운전자 배지가 없다 — 대기 중에만`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking {
            graph.session.selectDriver("u-1")
            graph.trip.startSession()
        }

        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.mainClock.advanceTimeBy(FULL_ENTRY_GUARD_MS + 1_100) // 세션 값은 1초 스로틀

        compose.onNodeWithText("기록 중").assertIsDisplayed()
        compose.onNodeWithText("김영업님").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1024dp-h640dp")
    fun `주차 중 오른쪽 위 운전자 배지를 누르면 운전자 선택으로 간다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        runBlocking { graph.session.selectDriver("u-1") }
        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.mainClock.advanceTimeBy(FULL_ENTRY_GUARD_MS + 100) // 진입 직후 오조작 방지 시간이 지난 뒤

        compose.onNodeWithText("김영업님").performClick()
        compose.waitForIdle()

        assertEquals(Route.ProfilePicker, graph.navigator.current)
    }

    @Test
    fun `주행이 시작되면 앱이 스스로 뒤로 물러난다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        var backgrounded = false
        compose.setContent { AutoLogRoot(graph, onRequestBackground = { backgrounded = true }) }

        graph.trip.setMotion(VehicleMotion.MOVING)
        compose.waitForIdle()

        assertTrue(backgrounded)
    }

    @Test
    fun `주차에서 내려가면 열린 화면이 닫히고 Home만 남는다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        graph.navigator.push(Route.ProfilePicker)
        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }

        graph.trip.setMotion(VehicleMotion.STOPPED)
        compose.waitForIdle()

        assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
    }
}

/** ViewModel(메인 루퍼)의 delay를 [ms]만큼 흘리고 화면을 맞춘다 — compose mainClock은 화면 쪽 시간만 센다. */
private fun ComposeContentTestRule.idleMain(ms: Long) {
    ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    waitForIdle()
}
