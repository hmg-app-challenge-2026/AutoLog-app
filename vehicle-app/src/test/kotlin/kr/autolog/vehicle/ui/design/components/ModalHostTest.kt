package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.recordMotionFrames
import kr.autolog.vehicle.ui.design.AutoLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 팝업 열림·닫힘(모션 계획 Task 3): 닫혀도 잠깐 남아 줄며 사라지고, 그동안은 누를 수 없다. 정지형이면 바로. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1268dp-mdpi")
class ModalHostTest {
    @get:Rule val compose = createComposeRule()

    private var message by mutableStateOf<String?>("놓친 운행")
    private var clicks = 0

    private fun show(staticMotion: Boolean = false) = compose.setContent {
        AutoLogTheme(staticMotion = staticMotion) {
            Box(Modifier.fillMaxSize().background(AutoLogTheme.colors.bg)) {
                ModalHost(message) { text ->
                    ModalCard(footer = { PrimaryButton("추가", onClick = { clicks++ }) }) { Text(text) }
                }
            }
        }
    }

    @Test
    fun `값이 null이 돼도 잠깐 마지막 내용으로 남았다가 사라진다`() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            message = null
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("놓친 운행").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("놓친 운행").assertDoesNotExist()
    }

    @Test
    fun `닫히는 동안에는 버튼을 눌러도 아무 일 없다`() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            message = null
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("추가").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `정지형(정차·애니메이션 줄이기)이면 바로 사라진다 — 기다린 시간이 한두 프레임`() {
        show(staticMotion = true)
        compose.waitForIdle()
        val before = compose.mainClock.currentTime
        compose.runOnUiThread { message = null }
        compose.waitForIdle()
        compose.onNodeWithText("놓친 운행").assertDoesNotExist()
        assertTrue(compose.mainClock.currentTime - before <= 48)
    }

    @Test
    fun `프레임 — 열림과 닫힘`() {
        message = null
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeBy(100)
        compose.recordMotionFrames("modal_open") { message = "놓친 운행" }
        compose.recordMotionFrames("modal_close") { message = null }
    }
}
