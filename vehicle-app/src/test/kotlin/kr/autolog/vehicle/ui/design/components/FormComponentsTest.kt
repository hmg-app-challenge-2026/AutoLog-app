package kr.autolog.vehicle.ui.design.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h640dp")
class FormComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `키패드로 숫자를 넣고 지운다`() {
        var value = ""
        compose.setContent {
            AutoLogTheme {
                var v by remember { mutableStateOf("") }
                NumericKeypad(onDigit = {
                    v += it
                    value = v
                }, onDelete = {
                    v = v.dropLast(1)
                    value = v
                }, allowDecimal = true)
            }
        }

        compose.onNodeWithText("6").performClick()
        compose.onNodeWithText("8").performClick()
        compose.onNodeWithText(".").performClick()
        compose.onNodeWithContentDescription("지우기").performClick()

        assertEquals("68", value)
    }

    @Test
    fun `잠금이면 입력 필드가 비활성이고 이유를 보여 준다`() {
        compose.setContent {
            AutoLogTheme {
                CompositionLocalProvider(LocalInputLocked provides true) {
                    InputField(label = "서버 주소", value = "https://", onValueChange = {})
                }
            }
        }

        compose.onNodeWithText("https://").assertIsNotEnabled() // 비활성 필드엔 SetText 동작이 없다
        compose.onNodeWithText("지금은 입력할 수 없어요").assertExists()
    }

    @Test
    fun `QR 매트릭스는 여백 4모듈을 포함한다`() {
        val m = qrMatrix("https://autolog.example/r/abcd1234?t=demo")

        assertTrue(m.width >= 21 + 8)
        assertTrue((0 until 4).all { !m.get(it, it) }) // 왼쪽 위 여백은 비어 있다
    }
}
