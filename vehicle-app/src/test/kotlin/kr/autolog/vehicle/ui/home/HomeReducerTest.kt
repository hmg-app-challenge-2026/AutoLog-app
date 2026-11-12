package kr.autolog.vehicle.ui.home

import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.data.repository.ActiveTrip
import kr.autolog.vehicle.data.repository.AutoEndReason
import kr.autolog.vehicle.data.repository.HistoryItem
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.ui.trips.TripDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeReducerTest {
    private val trip = ActiveTrip("t-1", startTime = 0L, origin = "본사")
    private val month = MonthSummary(tripCount = 23, totalKm = 412.6, businessKm = 388.1, reviewCount = 1)

    @Test
    fun `대기면 이번 달 요약이 주인공이고 놓친 운행 거리를 묻는다`() {
        val s = reduceHome(TripSession.Idle, 15.0, destination = null, now = 0, month = month)

        assertEquals(HomePhase.READY, s.phase)
        assertEquals(MonthUi(23, "412.6", "388.1"), s.month)
        assertEquals("15.0", s.missedKm)
    }

    @Test
    fun `기록 중에는 놓친 운행을 묻지 않는다 — 지금 운행이 먼저`() {
        val s = reduceHome(TripSession.Recording(trip, 23.4), 15.0, "판교", now = 0)

        assertNull(s.missedKm)
    }

    @Test
    fun `기록 중이면 거리·경로·경과 분`() {
        val s = reduceHome(TripSession.Recording(trip, 23.4), null, destination = "판교", now = 35 * 60_000L)

        assertEquals(HomePhase.RECORDING_PARKED, s.phase)
        assertEquals("23.4", s.tripKm)
        assertEquals("본사", s.origin)
        assertEquals("판교", s.destination)
        assertEquals(35, s.elapsedMin)
    }

    @Test
    fun `종료 대기여도 Home은 기록 중 그대로 — 남은 초는 정차 중 상태 줄용으로만 계산한다`() {
        val candidate = TripSession.AutoEnding(trip, 23.4, AutoEndReason.NO_MOVEMENT, deadlineAt = 163_000)

        val s = reduceHome(candidate, null, destination = "판교", now = 2_000)

        assertEquals(HomePhase.RECORDING_PARKED, s.phase)
        assertEquals("23.4", s.tripKm)
        assertEquals(161, s.autoEndInSec) // 주차 중엔 운행 결과 카드가 덮는다
    }

    @Test
    fun `기록 중이면 남은 초가 없다`() {
        assertEquals(null, reduceHome(TripSession.Recording(trip, 1.0), null, null, now = 0).autoEndInSec)
    }

    @Test
    fun `복구 상황이면 늘어난 거리를 보여 준다`() {
        val s = reduceHome(TripSession.NeedsRecovery(3.2), null, destination = null, now = 0)

        assertEquals(HomePhase.NEEDS_RECOVERY, s.phase)
        assertEquals("3.2", s.recoveryExtraKm)
    }

    @Test
    fun `계기판 거리를 못 읽으면 거리는 비우고 추정하지 않는다`() {
        val s = reduceHome(TripSession.Recording(trip, liveKm = null), null, "판교", now = 0)

        assertEquals(HomePhase.RECORDING_PARKED, s.phase)
        assertNull(s.tripKm)
    }

    @Test
    fun `최근 운행은 최대 3건, 오늘·어제·그 전으로 나누고 목적이 없으면 비워 둔다`() {
        val now = java.time.ZonedDateTime.of(
            2026,
            11,
            9,
            12,
            0,
            0,
            0,
            kr.autolog.vehicle.common.AppZone,
        ).toInstant().toEpochMilli()
        fun at(day: Int, h: Int) = java.time.ZonedDateTime.of(
            2026,
            11,
            day,
            h,
            12,
            0,
            0,
            kr.autolog.vehicle.common.AppZone,
        ).toInstant().toEpochMilli()
        fun item(id: String, start: Long, purpose: PurposeDetail?) =
            HistoryItem(id, start, start + 1, "김영업", false, "본사", "판교", 23.4, purpose, null)
        val recent =
            listOf(
                item("a", at(9, 9), null),
                item("b", at(8, 17), PurposeDetail.CLIENT_VISIT),
                item("c", at(6, 13), PurposeDetail.FIELD_WORK),
                item("d", at(5, 8), PurposeDetail.FIELD_WORK),
            )

        val s = reduceHome(TripSession.Idle, null, null, now = now, recent = recent)

        assertEquals(listOf("a", "b", "c"), s.recentTrips.map { it.tripId })
        assertEquals(listOf(TripDay.TODAY, TripDay.YESTERDAY, TripDay.EARLIER), s.recentTrips.map { it.day })
        assertEquals("09:12", s.recentTrips[0].time)
        assertNull(s.recentTrips[0].purpose)
        assertEquals("거래처 방문", s.recentTrips[1].purpose)
        assertEquals(6, s.recentTrips[2].dayOfMonth)
    }
}
