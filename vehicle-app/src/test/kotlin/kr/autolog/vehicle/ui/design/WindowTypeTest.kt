package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** 창 크기별 글자 역할 표를 고정한다 — 넓은 창과 Pleos 1:1 분할 한 칸은 같고, 세로로 긴 창만 일부가 다르다(디자인 언어 6.2). */
class WindowTypeTest {
    private val t = autoLogType()

    @Test
    fun `넓은 창 — 화면 제목·운행 결과 제목 display, 팝업 제목 headline, 분류 문구 title`() {
        val w = windowTypeOf(WindowLayout.WIDE, t)
        assertEquals(t.display, w.screenTitle)
        assertEquals(t.headline, w.modalTitle)
        assertEquals(t.display, w.resultTitle)
        assertEquals(t.title, w.resultClassification)
        assertEquals(t.title, w.resultClassification)
    }

    @Test
    fun `분할 화면 한 칸은 넓은 창과 같은 글자 — Home 운행 경로만 한 단계 크게(headline)`() {
        assertEquals(
            windowTypeOf(WindowLayout.WIDE, t).copy(homeRoute = t.headline),
            windowTypeOf(WindowLayout.NARROW, t),
        )
    }

    @Test
    fun `세로 창 — 글자는 넓은 창과 같고 결과 제목·Home 운행 경로는 title, 분류 문구는 label`() {
        val w = windowTypeOf(WindowLayout.WIDE, t)
        val tall = windowTypeOf(WindowLayout.TALL, t)
        assertEquals(w.copy(resultTitle = t.title, resultClassification = t.label, homeRoute = t.title), tall)
    }
}
