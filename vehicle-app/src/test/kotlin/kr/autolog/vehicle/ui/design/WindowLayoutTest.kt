package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
    @Test
    fun `가로로 넓은 창은 WIDE`() {
        assertEquals(WindowLayout.WIDE, windowLayoutOf(1050f, 840f)) // aspect 1.25, width 840 이상
        assertEquals(WindowLayout.WIDE, windowLayoutOf(1024f, 640f))
    }

    @Test
    fun `가로 비율이어도 840dp 미만이면 NARROW`() {
        assertEquals(WindowLayout.NARROW, windowLayoutOf(839f, 500f))
    }

    @Test
    fun `세로로 긴 창은 TALL`() {
        assertEquals(WindowLayout.TALL, windowLayoutOf(700f, 1000f)) // aspect 0.70
        assertEquals(WindowLayout.TALL, windowLayoutOf(720f, 1100f))
    }

    @Test
    fun `분할 화면 한 칸과 정사각에 가까운 창은 NARROW`() {
        assertEquals(WindowLayout.NARROW, windowLayoutOf(504f, 640f)) // aspect 0.79 — 분할 화면 한 칸
        assertEquals(WindowLayout.NARROW, windowLayoutOf(504f, 600f)) // aspect 0.84
        assertEquals(WindowLayout.NARROW, windowLayoutOf(900f, 800f)) // aspect 1.125
    }
}
