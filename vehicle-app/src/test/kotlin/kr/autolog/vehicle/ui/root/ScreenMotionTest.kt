package kr.autolog.vehicle.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.recordMotionFrames
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import kr.autolog.vehicle.ui.home.HomePhase
import kr.autolog.vehicle.ui.home.HomeScreen
import kr.autolog.vehicle.ui.home.HomeUiState
import kr.autolog.vehicle.ui.home.MonthUi
import kr.autolog.vehicle.ui.result.TripResultScreen
import kr.autolog.vehicle.ui.result.TripResultUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면 안 상태 전환 프레임(모션 계획 Task 5) — Home 대기 → 기록 중, 운행 결과 보기 → 수정 → 저장 완료. 검토용 시트만 남긴다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1268dp-mdpi")
class ScreenMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AutoLogTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(AutoLogTheme.colors.bg)) { WindowLayoutHost { content() } }
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun `Home 대기 → 기록 중`() {
        var home by mutableStateOf(HomeUiState(HomePhase.READY, month = MonthUi(23, "412.6", "388.1")))
        show { HomeScreen(home, onAction = {}) }
        compose.recordMotionFrames("home_start") {
            home =
                HomeUiState(
                    HomePhase.RECORDING_PARKED,
                    tripKm = "0.0",
                    origin = "본사",
                    destination = "판교 거래처",
                    elapsedMin = 0,
                )
        }
    }

    @Test
    fun `운행 결과 보기 → 수정 → 저장 완료`() {
        val viewing = TripResultUiState(
            loading = false,
            km = "48.2",
            minutes = 52,
            origin = "판교 테크노밸리",
            destination = "서울 강남구 역삼동",
            autoCloseRemainingMs = 3_000,
            canContinue = true,
        )
        var result by mutableStateOf(viewing)
        show { TripResultScreen(result, onAction = {}) }
        compose.recordMotionFrames("result_edit") { result = viewing.copy(editing = true, autoCloseRemainingMs = null) }
        compose.recordMotionFrames("result_saved") { result = result.copy(saved = true, editing = false) }
    }
}
