package kr.autolog.vehicle.ui.root

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.screenshot.recordMotionFrames
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면 전환 방향(모션 계획 Task 2): 전환 중엔 두 화면이 함께 있고, 끝나면 새 화면만. 프레임 시트도 남긴다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1268dp-mdpi")
class NavMotionTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun open(vararg stack: Route): TestAppGraph {
        val graph = TestAppGraph(scope, start = stack.first())
        stack.drop(1).forEach { graph.navigator.push(it) }
        compose.mainClock.autoAdvance = false
        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.mainClock.advanceTimeBy(FULL_ENTRY_GUARD_MS + 1_000)
        return graph
    }

    @Test
    fun `앞으로 — 전환 중엔 두 화면이 함께 있고 끝나면 새 화면만`() {
        val graph = open(Route.Home)
        compose.recordMotionFrames("push_home_settings") { graph.navigator.push(Route.Settings) }
        compose.onNodeWithText("설정").assertExists()
        compose.onNodeWithText("기록 시작").assertDoesNotExist()
    }

    @Test
    fun `중간 프레임엔 나가는 화면이 아직 있다`() {
        val graph = open(Route.Home)
        compose.runOnUiThread { graph.navigator.push(Route.Settings) }
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithText("기록 시작").assertExists() // 나가는 Home
        compose.onAllNodesWithText("설정").assertCountEquals(2) // Home 하단 타일 + 들어오는 설정 머리 줄
    }

    @Test
    fun `뒤로 — 떠나는 화면이 오른쪽으로 빠진다`() {
        val graph = open(Route.Home, Route.Settings)
        compose.recordMotionFrames("pop_settings_home") { graph.navigator.pop() }
        compose.onNodeWithText("기록 시작").assertExists()
    }

    @Test
    fun `운행 결과 — 아래에서 떠오르고 내려가며 사라진다`() {
        val graph = open(Route.Home)
        compose.recordMotionFrames("present_trip_result") { graph.navigator.push(Route.TripResult("t-s1")) }
        compose.recordMotionFrames("dismiss_trip_result") { graph.navigator.pop() }
    }

    @Test
    fun `같은 자리 교체 — QR에서 주유 입력 창으로`() {
        val graph = open(Route.Home, Route.ReceiptQr)
        compose.recordMotionFrames("fade_through_qr_fuel") { graph.navigator.replaceTop(Route.FuelEntry()) }
    }

    @Test
    fun `주차에서 정차로 — 같은 Home 그대로, 타일만 흐려진다`() {
        val graph = open(Route.Home)
        compose.recordMotionFrames("tier_full_to_stopped") { graph.trip.setMotion(VehicleMotion.STOPPED) }
    }
}
