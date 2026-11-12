package kr.autolog.vehicle.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.RecoveryAction
import kr.autolog.vehicle.screenshot.SampleDriver
import kr.autolog.vehicle.screenshot.ShotDriver
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import kr.autolog.vehicle.ui.trips.TripDay
import kr.autolog.vehicle.ui.trips.TripRowUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val month = MonthUi(tripCount = 23, totalKm = "412.6", businessKm = "388.1")

private val recent =
    listOf(
        TripRowUi("t-1", "본사", "판교 테크노밸리 거래처", "23.4", TripDay.TODAY, 11, 9, "09:12", null),
        TripRowUi("t-2", "판교 테크노밸리 거래처", "본사", "22.8", TripDay.YESTERDAY, 11, 8, "17:40", "거래처 방문"),
        TripRowUi("t-3", "본사", "수원 물류센터", "41.2", TripDay.EARLIER, 11, 6, "13:05", "외근"),
    )

private val ready = HomeUiState(phase = HomePhase.READY, month = month, recentTrips = recent)

/** 앱이 꺼진 동안 15 km를 달린 뒤의 대기 — 놓친 운행 팝업(F-10, D-26). */
private val readyMissed = ready.copy(missedKm = "15.0")

private val recording =
    HomeUiState(
        phase = HomePhase.RECORDING_PARKED,
        tripKm = "23.4",
        origin = "본사",
        destination = "판교 테크노밸리 거래처",
        elapsedMin = 35,
    )

/** 계기판 거리를 못 읽을 때(이유는 거리 칸 안 한 줄) + 긴 목적지 이름. */
private val recordingTrouble =
    recording.copy(
        tripKm = null,
        destination = "경기도 성남시 분당구 판교역로 테크노밸리 제2캠퍼스 거래처 본관",
    )
private val recovery = HomeUiState(phase = HomePhase.NEEDS_RECOVERY, recoveryExtraKm = "3.2")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h640dp")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: HomeUiState, actions: MutableList<HomeAction> = mutableListOf()) = compose.setContent {
        AutoLogTheme { WindowLayoutHost { HomeScreen(state, onAction = { actions += it }) } }
    }

    @Test
    fun `삭제는 한 번 더 확인한다`() {
        val actions = mutableListOf<HomeAction>()
        show(recovery, actions)

        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("지우기").performClick()

        assertEquals(listOf<HomeAction>(HomeAction.Recover(RecoveryAction.DELETE)), actions)
    }

    @Test
    fun `앱 안 알림이 없고 최근 운행 기록이 보인다 — 목적이 없으면 목적 미입력`() {
        show(ready)

        compose.onNodeWithText("최근 운행 기록").assertIsDisplayed()
        compose.onNodeWithText("본사 → 판교 테크노밸리 거래처").assertIsDisplayed()
        compose.onNodeWithText("오늘 09:12 · 23.4 km · 목적 미입력").assertIsDisplayed()
    }

    @Test
    fun `최근 운행 줄을 누르면 목적이 없어도 운행 기록 조회로 간다`() {
        val actions = mutableListOf<HomeAction>()
        show(ready, actions)

        compose.onNodeWithText("오늘 09:12 · 23.4 km · 목적 미입력").performClick()

        assertEquals(listOf<HomeAction>(HomeAction.OpenRecords), actions)
    }

    @Test
    fun `놓친 운행 팝업 — 추가하기 1탭, X는 무시`() {
        val actions = mutableListOf<HomeAction>()
        show(readyMissed, actions)

        compose.onNodeWithText("앱이 꺼진 동안 15.0 km를 달렸어요").assertIsDisplayed()
        compose.onNodeWithText("운행으로 추가하기").performClick()
        compose.onNodeWithContentDescription("무시").performClick()

        assertEquals(listOf(HomeAction.AcceptMissedTrip, HomeAction.IgnoreMissedTrip), actions)
    }

    @Test
    fun `계기판 거리를 못 읽으면 거리 칸 안에서 이유를 말한다`() {
        show(recordingTrouble)

        compose.onNodeWithText("거리 미확정").assertIsDisplayed()
        compose.onNodeWithText("계기판 거리를 읽지 못하고 있어요").assertIsDisplayed()
    }

    @Test
    fun `타일 4개 — 운행 기록 조회·설정·기록 시작`() {
        val actions = mutableListOf<HomeAction>()
        show(ready, actions)

        compose.onNodeWithText("운행 기록 조회").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("기록 시작").performClick()

        assertEquals(listOf(HomeAction.OpenRecords, HomeAction.OpenSettings, HomeAction.StartTrip), actions)
    }

    @Test
    fun `기록 중 — 상태 줄은 점 + 기록 중 · 경과, 첫 칸 타일은 기록 종료`() {
        val actions = mutableListOf<HomeAction>()
        show(recording, actions)

        compose.onNodeWithText("기록 중").assertIsDisplayed()
        compose.onNodeWithText("35분 경과").assertIsDisplayed()
        compose.onNodeWithText("운행 경로").assertIsDisplayed()
        compose.onNodeWithText("기록 시작").assertDoesNotExist()
        compose.onNodeWithText("기록 종료").performClick()

        assertEquals(listOf<HomeAction>(HomeAction.EndNow), actions)
    }

    @Test
    fun `삭제 확인에서 X를 누르면 지우지 않고 앞 단계로 돌아간다`() {
        val actions = mutableListOf<HomeAction>()
        show(recovery, actions)

        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithContentDescription("닫기").performClick()

        compose.onNodeWithText("기록하기").assertIsDisplayed()
        assertEquals(emptyList<HomeAction>(), actions)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeScreenshotTest {
    @Test
    fun ready() = captureAll("home_ready", driver = SampleDriver) { HomeScreen(ready, onAction = {}) }

    @Test
    fun missed() = captureAll("home_missed", driver = SampleDriver) { HomeScreen(readyMissed, onAction = {}) }

    @Test
    fun noDriver() = captureAll("home_nodriver", driver = ShotDriver(null, null)) { HomeScreen(ready, onAction = {}) }

    @Test
    fun recording() = captureAll("home_recording") { HomeScreen(recording, onAction = {}) }

    @Test
    fun recordingTrouble() = captureAll("home_recording_trouble") {
        HomeScreen(recordingTrouble, onAction = {})
    }

    @Test
    fun recovery() = captureAll("home_recovery") { HomeScreen(recovery, onAction = {}) }

    /** 정차 중(D-31): 같은 Home 읽기 전용 — 타일은 흐리게, [기록 종료]만 그대로. 상태 줄은 자동 종료까지 남은 시간. */
    @Test
    fun recordingStopped() = captureAll("home_recording_stopped") {
        Stopped { HomeScreen(recording.copy(autoEndInSec = 161), onAction = {}) }
    }

    /** 정차 중 운행이 없을 때(D-31): 대기 Home 읽기 전용 — 놓친 운행 팝업도 주차할 때까지 띄우지 않는다. */
    @Test
    fun readyStopped() = captureAll("home_ready_stopped") { Stopped { HomeScreen(readyMissed, onAction = {}) } }

    @Composable
    private fun Stopped(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalInputLocked provides true, LocalDrivingLocked provides true, content = content)
}
