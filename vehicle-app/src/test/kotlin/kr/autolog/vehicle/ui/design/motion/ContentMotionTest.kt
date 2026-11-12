package kr.autolog.vehicle.ui.design.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.recordMotionFrames
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ChoiceRow
import kr.autolog.vehicle.ui.design.components.PeriodSwitcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** 화면 안 작은 변화(모션 계획 Task 4): 달 이동은 옛 달과 새 달이 잠깐 함께 있다가 새 달만 남는다. 프레임 시트도 남긴다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1268dp-mdpi")
class ContentMotionTest {
    @get:Rule val compose = createComposeRule()

    private var month by mutableStateOf(YearMonth.of(2026, 11))
    private var selected by mutableStateOf(0)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AutoLogTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().background(AutoLogTheme.colors.bg).padding(Space.s7)) {
                    PeriodSwitcher(month, canGoNext = true, onPrev = {}, onNext = {})
                    listOf("일반 업무", "출퇴근", "개인 용무").forEachIndexed { i, text ->
                        ChoiceRow(text, selected = selected == i, onClick = { selected = i })
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun `달 이동 — 옛 달이 밀려나는 동안 새 달이 들어오고, 끝나면 새 달만`() {
        show()
        compose.runOnUiThread {
            month = month.minusMonths(1)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("2026년 11월").assertExists()
        compose.onNodeWithText("2026년 10월").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("2026년 11월").assertDoesNotExist()
    }

    @Test
    fun `프레임 — 이전 달, 목적 선택`() {
        show()
        compose.recordMotionFrames("month_prev") { month = month.minusMonths(1) }
        compose.recordMotionFrames("choice_select") { selected = 1 }
    }
}
