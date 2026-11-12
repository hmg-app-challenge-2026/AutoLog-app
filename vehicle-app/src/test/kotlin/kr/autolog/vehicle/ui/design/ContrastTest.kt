package kr.autolog.vehicle.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private fun assertAtLeast(name: String, fg: Color, backgrounds: List<Color>, min: Double) {
        backgrounds.forEach { bg ->
            val ratio = contrastRatio(fg, bg)
            assertTrue("$name 대비 $ratio < $min", ratio >= min)
        }
    }

    private fun AutoLogColors.textTokens() = mapOf(
        "textPrimary" to textPrimary,
        "textSecondary" to textSecondary,
        "textTertiary" to textTertiary,
        "accent" to accent,
        "success" to success,
        "warning" to warning,
        "danger" to danger,
    )

    @Test
    fun `흰색과 검정의 대비는 21이다`() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
    }

    @Test
    fun `다크 텍스트 토큰은 모든 서피스에서 4점5 이상`() {
        DarkColors.textTokens().forEach { (name, c) -> assertAtLeast("dark.$name", c, DarkColors.surfaces, 4.5) }
    }

    @Test
    fun `라이트 텍스트 토큰은 모든 서피스에서 4점5 이상`() {
        LightColors.textTokens().forEach { (name, c) -> assertAtLeast("light.$name", c, LightColors.surfaces, 4.5) }
    }

    @Test
    fun `버튼 글자는 채움색 위에서 4점5 이상`() {
        listOf(DarkColors, LightColors).forEach {
            assertAtLeast("onAccent", it.onAccent, listOf(it.accentFill), 4.5)
            assertAtLeast("onDanger", it.onAccent, listOf(it.dangerFill), 4.5)
        }
    }

    @Test
    fun `비텍스트 요소는 모든 서피스에서 3 이상`() {
        listOf(DarkColors, LightColors).forEach {
            assertAtLeast("outlineStrong", it.outlineStrong, it.surfaces, 3.0)
            assertAtLeast("accentFill", it.accentFill, it.surfaces, 3.0)
            assertAtLeast("dangerFill", it.dangerFill, it.surfaces, 3.0)
        }
    }

    @Test
    fun `아바타 그라데이션 양 끝에서 흰 이니셜이 4점5 이상`() {
        AvatarGradients.forEachIndexed { i, (start, end) ->
            assertAtLeast("avatar$i", Color.White, listOf(start, end), 4.5)
        }
    }
}
