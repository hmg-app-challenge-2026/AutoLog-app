package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test

/** 디자인 언어 4·6장의 하한을 숫자로 강제한다. */
class TokenFloorTest {
    private val type = autoLogType()

    @Test
    fun `본문과 버튼 글자는 28sp 이상`() {
        assertTrue(type.body.fontSize.value >= 28f)
        assertTrue(type.label.fontSize.value >= 28f)
    }

    @Test
    fun `주요 정보 글자는 36sp 이상`() {
        assertTrue(type.title.fontSize.value >= 36f)
    }

    @Test
    fun `보조 글자 caption은 22sp, 가장 작은 글자 fine은 20sp 이상(Pleos 보조 3mm)`() {
        val all =
            listOf(
                type.hero,
                type.heroSmall,
                type.display,
                type.headline,
                type.title,
                type.body,
                type.label,
                type.section,
            )
        assertTrue(all.all { it.fontSize.value >= type.caption.fontSize.value })
        // 가장 작은 글자 caption(28 sp)이 글자 하한 — Pleos 에뮬레이터(160 dpi)에서 확인한 크기(PR #4, 2026-11-10)
        assertTrue(type.caption.fontSize.value >= 28f)
    }

    @Test
    fun `숫자 글자는 고정폭 숫자를 쓴다`() {
        assertTrue(type.hero.fontFeatureSettings == "tnum")
        assertTrue(type.heroSmall.fontFeatureSettings == "tnum")
    }

    @Test
    fun `터치 대상과 버튼은 76dp 이상`() {
        listOf(Size.touchMin, Size.buttonMd, Size.buttonLg, Size.buttonXl, Size.iconButton)
            .forEach { assertTrue("$it", it.value >= 76f) }
    }
}
