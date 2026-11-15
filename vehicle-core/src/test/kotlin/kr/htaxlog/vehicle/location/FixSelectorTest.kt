package kr.htaxlog.vehicle.location

import kr.htaxlog.vehicle.core.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixSelectorTest {
    private val now = 1_000_000L

    private fun fixAt(timestampMs: Long) = LocationFix(37.4, 127.1, 90f, 0.9f, timestampMs)

    @Test
    fun `30초 이내 좌표는 그대로 쓴다`() {
        val fix = fixAt(now - 30_000)

        assertEquals(fix, FixSelector.freshOrNull(fix, now))
    }

    @Test
    fun `30초보다 오래된 좌표는 쓰지 않는다`() {
        assertNull(FixSelector.freshOrNull(fixAt(now - 30_001), now))
    }

    @Test
    fun `미래 시각 좌표는 시계 오류로 보고 쓰지 않는다`() {
        assertNull(FixSelector.freshOrNull(fixAt(now + 5_000), now))
    }

    @Test
    fun `좌표가 없으면 null`() {
        assertNull(FixSelector.freshOrNull(null, now))
    }
}
