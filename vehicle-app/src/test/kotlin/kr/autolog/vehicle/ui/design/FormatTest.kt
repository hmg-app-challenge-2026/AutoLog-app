package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `거리는 천 단위 쉼표와 소수 1자리`() {
        assertEquals("18,420.5", formatKm(18_420.5))
        assertEquals("23.4", formatKm(23.44))
        assertEquals("0.0", formatKm(0.0))
        assertNull(formatKm(null))
    }

    @Test
    fun `남은 초는 올림하고 0 아래로 내려가지 않는다`() {
        assertEquals(5, remainingSeconds(deadlineAt = 10_000, now = 5_000))
        assertEquals(5, remainingSeconds(deadlineAt = 10_000, now = 5_001))
        assertEquals(0, remainingSeconds(deadlineAt = 10_000, now = 12_000))
    }
}
