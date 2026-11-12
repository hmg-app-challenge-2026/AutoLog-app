package kr.autolog.vehicle.ui.history

import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.HistoryItem
import kr.autolog.vehicle.data.repository.MonthSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class HistoryReducerTest {
    private val nov = YearMonth.of(2026, 11)
    private val now = 1_794_000_000_000 // 2026-11 중순

    private fun item(id: String, driver: String?) = HistoryItem(
        tripId = id,
        startTime = now - 3_600_000,
        endTime = now,
        driverName = driver,
        driverUnset = driver == null,
        origin = "본사",
        destination = "판교",
        distanceKm = 23.4,
        purpose = null,
        upload = null,
    )

    private val drivers = listOf("u-1", "u-2", "u-3", "u-4").map { DriverProfile(it, "이름$it", null) }

    @Test
    fun `지금 달이면 다음 달로 넘어갈 수 없고 지난 달이면 넘어갈 수 있다`() {
        assertFalse(reduceHistory(nov, nov, emptyList(), null, emptyList(), now).canGoNext)
        assertTrue(reduceHistory(nov.minusMonths(1), nov, emptyList(), null, emptyList(), now).canGoNext)
    }

    @Test
    fun `불러오기 전엔 loading, 합계는 저장소 값 그대로`() {
        assertTrue(reduceHistory(nov, nov, null, null, emptyList(), now).loading)

        val s = reduceHistory(nov, nov, listOf(item("t-1", "김영업")), MonthSummary(6, 1234.56, 90.7, 1), drivers, now)

        assertFalse(s.loading)
        assertEquals(6, s.tripCount)
        assertEquals("1,234.6", s.totalKm)
        assertEquals("t-1", s.rows.single().tripId)
    }

    @Test
    fun `운전자 미확정 줄을 표시하고 후보는 추천 순 최대 3명`() {
        val s = reduceHistory(nov, nov, listOf(item("t-1", null)), null, drivers, now)

        assertTrue(s.rows.single().driverUnset)
        assertEquals(listOf("u-1", "u-2", "u-3"), s.driverChoices.map { it.userId })
    }
}
