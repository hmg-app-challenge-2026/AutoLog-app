package kr.autolog.vehicle.ui.design.components

import kr.autolog.vehicle.ui.design.AvatarGradients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTest {
    @Test
    fun `같은 사람은 항상 같은 색`() {
        assertEquals(avatarGradientIndex("u-1"), avatarGradientIndex("u-1"))
    }

    @Test
    fun `색 번호는 팔레트 범위 안`() {
        listOf("u-1", "u-2", "local-9", "", "가나다").forEach {
            assertTrue(avatarGradientIndex(it) in AvatarGradients.indices)
        }
    }

    @Test
    fun `한글 이름은 첫 글자, 영문 이름은 두 단어 첫 글자`() {
        assertEquals("김", initialsOf("김영업"))
        assertEquals("JS", initialsOf("john smith"))
        assertEquals("A", initialsOf("Alice"))
        assertEquals("?", initialsOf("  "))
    }
}
