package kr.htaxlog.vehicle.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreanNumberTest {
    private fun p(s: String) = KoreanNumber.parse(s)

    @Test
    fun `아라비아 숫자와 쉼표`() {
        assertEquals(68_000.0, p("68,000")!!, 0.0)
        assertEquals(40.5, p("40.5")!!, 0.0)
    }

    @Test
    fun `숫자와 만·천 섞어 쓰기`() {
        assertEquals(68_000.0, p("6만8천")!!, 0.0)
        assertEquals(125_000.0, p("12만5천")!!, 0.0)
        assertEquals(1_689.0, p("1천6백89")!!, 0.0)
    }

    @Test
    fun `한글 수사`() {
        assertEquals(70_000.0, p("칠만")!!, 0.0)
        assertEquals(68_000.0, p("육만팔천")!!, 0.0)
        assertEquals(40.0, p("사십")!!, 0.0)
        assertEquals(10_000.0, p("만")!!, 0.0)
        assertEquals(1_689.0, p("천육백팔십구")!!, 0.0)
    }

    @Test
    fun `점으로 소수`() {
        assertEquals(40.5, p("사십점오")!!, 0.0)
    }

    @Test
    fun `숫자가 아니면 null`() {
        assertNull(p("리터"))
        assertNull(p(""))
    }
}
