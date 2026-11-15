package kr.htaxlog.relay.admin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ScheduleTimeParseTest {
    private val nineKst = Instant.parse("2026-11-16T00:00:00Z") // 2026-11-16 09:00 KST

    @Test
    fun `배차 CSV 시각은 엑셀이 저장하는 모양도 받는다`() {
        listOf(
            "2026-11-16 09:00",
            "2026-11-16 9:00",
            "2026/11/16 9:00",
            "2026.11.16 9:00",
            "2026-11-16T09:00",
            "2026-11-16 09:00:00",
            "2026-11-16T09:00:00+09:00",
        ).forEach { assertEquals(nineKst, AdminReferenceService.parseTime(it), it) }
    }

    @Test
    fun `시각 형식이 아니면 거부한다`() {
        assertThrows<Exception> { AdminReferenceService.parseTime("11월 16일 아침") }
    }
}
