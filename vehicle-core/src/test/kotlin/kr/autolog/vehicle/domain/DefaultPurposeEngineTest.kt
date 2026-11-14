package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeRuleId
import kr.autolog.vehicle.core.RecentUse
import kr.autolog.vehicle.core.ScheduleInfo
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.TripFacts
import kr.autolog.vehicle.core.TripSummary
import kr.autolog.vehicle.core.UserBadge
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.fake.InMemoryDomainStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 저장소(InMemoryDomainStore)에서 읽어 규칙에 넘기는 연결부를 확인한다. 규칙 자체는 PurposeRulesTest. */
class DefaultPurposeEngineTest {
    private val store =
        InMemoryDomainStore().apply {
            placeList += PlaceInfo("home", "자택", PlaceKind.HOME, 37.50, 127.00)
            placeList += PlaceInfo("office", "본사", PlaceKind.OFFICE, 37.498, 127.028)
            placeList += PlaceInfo("client", "판교 거래처", PlaceKind.CLIENT, 37.401, 127.108)
            userList.value = listOf(UserInfo("u-kim", "김영업"), UserInfo("u-lee", "이현장"), UserInfo("u-park", "박관리"))
        }
    private val engine = DefaultPurposeEngine(store, newId = { "new-place" })

    private fun trip(start: Long, from: Pair<Double, Double>?, to: Pair<Double, Double>?) = TripFacts(
        tripId = "t-now",
        vehicleId = "car-1",
        startTime = start,
        endTime = start + HOUR,
        startLat = from?.first,
        startLng = from?.second,
        endLat = to?.first,
        endLng = to?.second,
    )

    private val home = 37.50 to 127.00
    private val office = 37.498 to 127.028
    private val client = 37.401 to 127.108
    private val unknown = 37.30 to 127.20

    @Test
    fun `좌표를 등록 장소에 맞춰 출근을 추천한다`() = runTest {
        val s = engine.suggest(trip(kst(8), home, office))

        assertEquals(PurposeDetail.COMMUTE_IN, s.detail)
    }

    @Test
    fun `등록 장소 반경 안의 좌표면 그 장소를 돌려준다`() = runTest {
        assertEquals("본사", engine.placeAt(office.first, office.second)?.label)
    }

    @Test
    fun `어느 등록 장소에도 속하지 않으면 null이다`() = runTest {
        assertEquals(null, engine.placeAt(35.1, 129.0))
    }

    @Test
    fun `배차가 있어도 목적은 배차로 정하지 않는다`() = runTest {
        store.scheduleList += "car-1" to ScheduleInfo("s-1", "u-kim", kst(14), kst(15), "client", "MEETING")

        val s = engine.suggest(trip(kst(13, 30), office, client))

        assertTrue(s.scheduleId == null)
        assertTrue(s.rule != PurposeRuleId.SCHEDULE)
    }

    @Test
    fun `같은 목적지의 과거 확정 목적을 읽는다`() = runTest {
        store.placeList += PlaceInfo("lab", "연구소", PlaceKind.OFFICE, 37.30, 127.20)
        repeat(3) { i ->
            store.tripList +=
                TripSummary(
                    tripId = "p$i",
                    vehicleId = "car-1",
                    userId = "u-kim",
                    startTime = kst(9, day = 1) - i * 24 * HOUR,
                    endTime = null,
                    endLat = 37.30,
                    endLng = 127.20,
                    confirmedPurpose = PurposeDetail.FIELD_WORK,
                )
        }

        val s = engine.suggest(trip(kst(13), office, unknown))

        assertEquals(PurposeRuleId.PAST_CONFIRMED, s.rule)
        assertEquals(PurposeDetail.FIELD_WORK, s.detail)
    }

    @Test
    fun `운전자 후보는 배차에서 찾는다`() = runTest {
        store.scheduleList += "car-1" to ScheduleInfo("s-1", "u-lee", kst(9, 30), kst(11))

        val c = engine.driverCandidate(trip(kst(10), office, client))!!

        assertEquals("이현장", c.name)
        assertEquals(DriverSource.SCHEDULE, c.source)
    }

    @Test
    fun `운전자 선택 목록은 배차·최근 순으로 정렬된다`() = runTest {
        store.scheduleList += "car-1" to ScheduleInfo("s-1", "u-park", kst(10, 20), kst(11))
        store.recent += RecentUse("u-lee", 5)

        val cards = engine.orderedUsers("car-1", kst(10)).first()

        assertEquals(listOf("u-park", "u-lee", "u-kim"), cards.map { it.userId })
        assertEquals(UserBadge.SCHEDULED, cards[0].badge)
    }

    @Test
    fun `미등록 목적지를 업무로 확정하면 거래처 등록을 제안한다`() = runTest {
        assertTrue(engine.shouldProposePlace(trip(kst(13), office, unknown), PurposeDetail.CLIENT_VISIT))
        assertFalse(engine.shouldProposePlace(trip(kst(13), office, unknown), PurposeDetail.PERSONAL))
        assertFalse(engine.shouldProposePlace(trip(kst(13), office, client), PurposeDetail.CLIENT_VISIT))
    }

    @Test
    fun `거래처로 등록하면 LOCAL 장소가 생기고 다음 추천에 쓰인다`() = runTest {
        val r = engine.registerClientPlace(trip(kst(13), office, unknown), "  수원 신규 거래처 ")

        assertEquals(SettingsResult.Ok, r)
        val saved = store.placeList.single { it.placeId == "new-place" }
        assertEquals("수원 신규 거래처", saved.label)
        assertEquals(Origin.LOCAL, saved.origin)
        assertEquals(PurposeDetail.CLIENT_VISIT, engine.suggest(trip(kst(13), office, unknown)).detail)
    }

    @Test
    fun `운전자 없이 끝난 최근 운행을 후보와 함께 최신 순으로 묻는다`() = runTest {
        store.tripList +=
            TripSummary("old", "car-1", null, kst(9, day = 1) - 8 * 24 * HOUR, kst(10, day = 1) - 8 * 24 * HOUR)
        store.tripList += TripSummary("a", "car-1", "u-kim", kst(7), kst(7, 30))
        store.tripList +=
            TripSummary(
                "b",
                "car-1",
                null,
                kst(8),
                kst(8, 40),
                distanceKm = 23.4,
                originLabel = "본사",
                destinationLabel = "판교",
            )
        store.tripList += TripSummary("c", "car-1", null, kst(13), kst(13, 20))
        store.tripList += TripSummary("other", "car-2", null, kst(12), kst(12, 10))

        val q = engine.unassignedTrips("car-1", kst(15))

        assertEquals(listOf("c", "b"), q.map { it.tripId })
        assertEquals("08:00 출발 → 판교 · 23.4 km", q[1].label)
        assertEquals("u-kim", q[1].candidate?.userId) // 2시간 안 직전 운행(a)의 운전자
        assertEquals(null, q[0].candidate)
    }

    @Test
    fun `이미 등록된 곳은 다시 등록하지 않는다`() = runTest {
        val r = engine.registerClientPlace(trip(kst(13), office, client), "판교")

        assertTrue(r is SettingsResult.Invalid)
    }
}
