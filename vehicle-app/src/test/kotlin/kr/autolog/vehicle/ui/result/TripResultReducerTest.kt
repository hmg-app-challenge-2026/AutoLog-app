package kr.autolog.vehicle.ui.result

import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.data.repository.TripResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripResultReducerTest {
    private val trip =
        TripResult("t-1", 0L, 52 * 60_000L, 48.2, "판교 테크노밸리", "서울 강남구 역삼동", PurposeCategory.BUSINESS, false)

    @Test
    fun `거리·시간·경로와 기본 분류(일반 업무)`() {
        val s = reduceTripResult(trip, loaded = true, TripResultInteraction(autoCloseAt = 5_000), now = 0)

        assertEquals("48.2", s.km)
        assertEquals(52, s.minutes)
        assertEquals(PurposeCategory.BUSINESS, s.selected)
        assertEquals(5_000L, s.autoCloseRemainingMs)
        assertEquals(5, s.autoCloseSec)
    }

    @Test
    fun `사용자가 고른 분류가 저장값보다 먼저, 끝 시각이 없으면 시간 미상`() {
        val s =
            reduceTripResult(
                trip.copy(endTime = null),
                loaded = true,
                TripResultInteraction(selected = PurposeCategory.COMMUTE),
                now = 0,
            )

        assertEquals(PurposeCategory.COMMUTE, s.selected)
        assertNull(s.minutes)
    }

    @Test
    fun `읽기 전 loading, 운행이 없으면 notFound`() {
        assertTrue(reduceTripResult(null, loaded = false, TripResultInteraction(), now = 0).loading)
        assertTrue(reduceTripResult(null, loaded = true, TripResultInteraction(), now = 0).notFound)
    }

    @Test
    fun `종료 대기 중이면 남은 시간은 종료 시각까지, 계속 기록 가능 — 수정 중엔 남은 시간 없음`() {
        val ending = trip.copy(endsAt = 5_000)

        val viewing = reduceTripResult(ending, loaded = true, TripResultInteraction(autoCloseAt = 99_000), now = 2_000)
        val editing = reduceTripResult(ending, loaded = true, TripResultInteraction(editing = true), now = 2_000)

        assertEquals(3, viewing.autoCloseSec)
        assertEquals(3_000L, viewing.autoCloseRemainingMs)
        assertTrue(viewing.canContinue)
        assertNull(editing.autoCloseSec)
    }

    @Test
    fun `남은 시간은 마감까지 ms — 300ms 남으면 글자는 1초, 마감이 지나면 0`() {
        val at = TripResultInteraction(autoCloseAt = 5_000)

        assertEquals(1, reduceTripResult(trip, loaded = true, at, now = 4_700).autoCloseSec)
        assertEquals(300L, reduceTripResult(trip, loaded = true, at, now = 4_700).autoCloseRemainingMs)
        assertEquals(0L, reduceTripResult(trip, loaded = true, at, now = 6_000).autoCloseRemainingMs)
    }
}
