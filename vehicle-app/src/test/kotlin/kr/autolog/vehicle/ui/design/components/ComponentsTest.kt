package kr.autolog.vehicle.ui.design.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.ConnectCode
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `잠금이면 버튼이 비활성이고 눌러도 실행되지 않는다`() {
        var clicks = 0
        compose.setContent {
            AutoLogTheme {
                CompositionLocalProvider(LocalInputLocked provides true) {
                    PrimaryButton("운행 시작", onClick = { clicks++ })
                }
            }
        }

        compose.onNodeWithText("운행 시작").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `잠금이 아니면 1탭으로 실행된다`() {
        var clicks = 0
        compose.setContent { AutoLogTheme { PrimaryButton("운행 시작", onClick = { clicks++ }) } }

        compose.onNodeWithText("운행 시작").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `코드 칸에 키보드로 친 글자는 값 규칙을 거쳐 칸마다 한 글자씩 보인다`() {
        compose.setContent {
            AutoLogTheme {
                var code by remember { mutableStateOf("") }
                CodeField("연결 코드 6자리", code, ConnectCode.LENGTH, { code = ConnectCode.normalize(it) })
            }
        }

        compose.onNode(hasSetTextAction()).performClick().performTextInput("7kq")

        listOf("7", "K", "Q").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `잠금이면 코드 칸에 글자를 넣을 수 없고 안내가 보인다`() {
        compose.setContent {
            AutoLogTheme {
                CompositionLocalProvider(LocalInputLocked provides true) {
                    CodeField("연결 코드 6자리", "", ConnectCode.LENGTH, {})
                }
            }
        }

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0) // 비활성 입력 칸은 글자 넣기 동작이 없다
        compose.onNodeWithText("지금은 입력할 수 없어요").assertExists()
    }
}

/** 스크린샷은 compose rule과 섞지 않고 별도 클래스로(각 캡처가 자기 Activity를 띄운다). */
