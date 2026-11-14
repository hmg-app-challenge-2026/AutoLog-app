package kr.autolog.vehicle.domain

import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeRouteRule
import kr.autolog.vehicle.core.PurposeRuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class PurposeRulesTest {
    private val rules = PurposeRules()

    private val home = Place("p-home", "자택", PlaceKind.HOME, 37.49, 127.02)
    private val office = Place("p-office", "강남 본사", PlaceKind.OFFICE, 37.50, 127.03)
    private val branch = Place("p-branch", "수원 지점", PlaceKind.BRANCH, 37.26, 127.03)
    private val client = Place("p-client", "판교 A고객사", PlaceKind.CLIENT, 37.40, 127.10)
    private val gas = Place("p-gas", "SK 판교주유소", PlaceKind.GAS_STATION, 37.39, 127.11)

    /** 2026-11-02 는 월요일, 2026-11-07 은 토요일. */
    private fun at(iso: String): Long = ZonedDateTime.parse("$iso+09:00[Asia/Seoul]").toInstant().toEpochMilli()

    @Test
    fun `a 배차 시각 2시간 이내이고 목적지가 같으면 배차 목적을 추천한다`() {
        val ctx =
            TripContext(
                startTime = at("2026-11-02T09:00:00"),
                origin = office,
                destination = client,
                schedules = listOf(ScheduleCandidate("s-1", at("2026-11-02T10:30:00"), client.placeId, "MEETING")),
            )

        val s = rules.suggest(ctx)

        assertEquals(PurposeDetail.MEETING, s.detail)
        assertEquals(0.97, s.confidence, 0.0)
        assertEquals("s-1", s.scheduleId)
    }

    @Test
    fun `a 배차가 2시간보다 멀면 배차 규칙은 쓰지 않는다`() {
        val ctx =
            TripContext(
                startTime = at("2026-11-02T09:00:00"),
                origin = office,
                destination = client,
                schedules = listOf(ScheduleCandidate("s-1", at("2026-11-02T11:01:00"), client.placeId, "MEETING")),
            )

        assertEquals(PurposeRuleId.CLIENT, rules.suggest(ctx).rule)
    }

    @Test
    fun `b 평일 아침 자택에서 회사로 가면 출근을 추천한다`() {
        val s = rules.suggest(TripContext(at("2026-11-02T08:10:00"), origin = home, destination = office))

        assertEquals(PurposeDetail.COMMUTE_IN, s.detail)
        assertEquals(PurposeCategory.COMMUTE, s.category)
        assertEquals(0.95, s.confidence, 0.0)
    }

    @Test
    fun `b 주말 아침 자택에서 회사는 출근으로 보지 않는다`() {
        val s = rules.suggest(TripContext(at("2026-11-07T08:10:00"), origin = home, destination = office))

        assertEquals(PurposeRuleId.FALLBACK, s.rule)
    }

    @Test
    fun `b 11시 이후 자택에서 회사는 출근으로 보지 않는다`() {
        val s = rules.suggest(TripContext(at("2026-11-02T11:00:00"), origin = home, destination = office))

        assertEquals(PurposeRuleId.FALLBACK, s.rule)
    }

    @Test
    fun `c 저녁에 회사에서 자택으로 가면 퇴근을 추천한다`() {
        val s = rules.suggest(TripContext(at("2026-11-02T18:30:00"), origin = office, destination = home))

        assertEquals(PurposeDetail.COMMUTE_OUT, s.detail)
        assertEquals(0.95, s.confidence, 0.0)
    }

    @Test
    fun `d 목적지가 거래처면 거래처 방문을 추천한다`() {
        val s = rules.suggest(TripContext(at("2026-11-02T14:00:00"), origin = office, destination = client))

        assertEquals(PurposeDetail.CLIENT_VISIT, s.detail)
        assertEquals(PurposeCategory.BUSINESS, s.category)
        assertEquals(0.90, s.confidence, 0.0)
    }

    @Test
    fun `e 목적지가 지점이면 지점 이동을 추천한다`() {
        val s = rules.suggest(TripContext(at("2026-11-02T14:00:00"), origin = office, destination = branch))

        assertEquals(PurposeDetail.BRANCH_MOVE, s.detail)
        assertEquals(0.90, s.confidence, 0.0)
    }

    @Test
    fun `f 같은 목적지에서 3번 이상 확정한 목적이 있으면 그것을 추천한다`() {
        val past = listOf(PurposeDetail.FIELD_WORK, PurposeDetail.FIELD_WORK, PurposeDetail.PERSONAL)

        val s = rules.suggest(TripContext(at("2026-11-02T14:00:00"), office, gas, pastConfirmedAtDestination = past))

        assertEquals(PurposeDetail.FIELD_WORK, s.detail)
        assertEquals(0.80, s.confidence, 0.0)
        assertTrue(s.isConfident)
    }

    @Test
    fun `f 과거 확정이 2번뿐이면 쓰지 않는다`() {
        val past = listOf(PurposeDetail.FIELD_WORK, PurposeDetail.FIELD_WORK)

        val s = rules.suggest(TripContext(at("2026-11-02T14:00:00"), office, gas, pastConfirmedAtDestination = past))

        assertEquals(PurposeRuleId.FALLBACK, s.rule)
    }

    @Test
    fun `g 주말에 미등록 장소로 가면 확인 필요를 낮은 확신으로 추천한다`() {
        val s = rules.suggest(TripContext(at("2026-11-07T14:00:00"), origin = home, destination = null))

        assertEquals(PurposeDetail.NEEDS_CHECK, s.detail)
        assertEquals(0.30, s.confidence, 0.0)
        assertFalse(s.isConfident)
    }

    @Test
    fun `h 평일 회사에서 미등록 장소로 가면 확인 필요`() {
        val s = rules.suggest(TripContext(at("2026-11-02T14:00:00"), origin = office, destination = null))

        assertEquals(PurposeDetail.NEEDS_CHECK, s.detail)
        assertEquals(PurposeCategory.UNDETERMINED, s.category)
        assertEquals(0.50, s.confidence, 0.0)
    }

    @Test
    fun `배차 목적 문자열을 모르면 거래처 방문으로 둔다`() {
        val ctx =
            TripContext(
                startTime = at("2026-11-02T09:00:00"),
                origin = office,
                destination = client,
                schedules = listOf(ScheduleCandidate("s-1", at("2026-11-02T09:00:00"), client.placeId, "UNKNOWN_X")),
            )

        assertEquals(PurposeDetail.CLIENT_VISIT, rules.suggest(ctx).detail)
    }

    private val delivery = PurposeDetail.custom("납품", PurposeCategory.BUSINESS)

    @Test
    fun `목적 규칙은 출발과 도착이 둘 다 맞아야 그 목적을 추천한다`() {
        val rule = PurposeRouteRule("r-1", office.placeId, client.placeId, delivery)
        val base = TripContext(at("2026-11-02T14:00:00"), office, client, routeRules = listOf(rule))

        val hit = rules.suggest(base)
        val otherOrigin = rules.suggest(base.copy(origin = branch))

        assertEquals(delivery, hit.detail)
        assertEquals(PurposeRuleId.ROUTE, hit.rule)
        assertEquals(PurposeRuleId.CLIENT, otherOrigin.rule) // 출발이 다르면 원래 규칙(거래처)로
    }

    @Test
    fun `출발 어디서든 규칙은 도착만 맞으면 쓰고 출발까지 맞는 규칙이 먼저다`() {
        val anywhere = PurposeRouteRule("r-any", null, client.placeId, PurposeDetail.MEETING)
        val exact = PurposeRouteRule("r-exact", office.placeId, client.placeId, delivery)
        val ctx = TripContext(at("2026-11-02T14:00:00"), branch, client, routeRules = listOf(anywhere, exact))

        assertEquals(PurposeRuleId.ROUTE_ANY_ORIGIN, rules.suggest(ctx).rule)
        assertEquals(delivery, rules.suggest(ctx.copy(origin = office)).detail)
    }
}
