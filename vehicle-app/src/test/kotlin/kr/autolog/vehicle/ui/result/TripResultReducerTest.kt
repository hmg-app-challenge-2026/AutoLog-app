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

    @Test
    fun `고친 거리가 있으면 그 거리와 수정됨, 주소는 그대로 넘긴다`() {
        val withAddress = trip.copy(originAddress = "경기 성남시 분당구 삼평동 판교역로 1", destinationAddress = "서울 강남구 역삼동 테헤란로 152")

        val s = reduceTripResult(withAddress, loaded = true, TripResultInteraction(editedKm = 50.0), now = 0)

        assertEquals("50.0", s.km)
        assertTrue(s.distanceEdited)
        assertEquals("경기 성남시 분당구 삼평동 판교역로 1", s.originAddress)
        assertEquals("서울 강남구 역삼동 테헤란로 152", s.destinationAddress)
    }

    @Test
    fun `저장된 수정 거리도 수정됨으로 보인다`() {
        val s = reduceTripResult(trip.copy(distanceEdited = true), loaded = true, TripResultInteraction(), now = 0)

        assertTrue(s.distanceEdited)
    }

    @Test
    fun `거리 숫자판은 소수 1자리까지만 받는다`() {
        assertEquals("12.3", pressDistanceKey("12.3", '4'))
        assertEquals("12.", pressDistanceKey("12", '.'))
        assertEquals("1500.0", pressDistanceKey("1500.", '0'))
        assertEquals("", distanceDraftOf(null))
        assertEquals("1234.5", distanceDraftOf(1_234.5))
    }

    @Test
    fun `거리는 0 초과 1500 km 이하만 받는다`() {
        assertEquals(DistanceInput.Invalid(DistanceEntryError.EMPTY_OR_ZERO), parseDistance(""))
        assertEquals(DistanceInput.Invalid(DistanceEntryError.EMPTY_OR_ZERO), parseDistance("0.0"))
        assertEquals(DistanceInput.Invalid(DistanceEntryError.TOO_LONG), parseDistance("1500.1"))
        assertEquals(DistanceInput.Valid(1_500.0), parseDistance("1500"))
        assertEquals(DistanceInput.Valid(0.1), parseDistance("0.1"))
    }
}
