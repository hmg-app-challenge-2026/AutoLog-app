package kr.autolog.vehicle.domain

import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeRouteRule
import kr.autolog.vehicle.core.PurposeRuleId
import kr.autolog.vehicle.core.Suggestion
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 목적 추천 입력. 모두 Room에 있는 값이라 차 안에서 계산한다(기술명세 3.3). */
data class TripContext(
    /** 운행 시작 시각(epoch millis). */
    val startTime: Long,
    /** 출발지가 매칭된 등록 장소. 없으면 null. */
    val origin: Place?,
    /** 도착지가 매칭된 등록 장소. 없으면 null(미등록). */
    val destination: Place?,
    /** 같은 목적지에서 과거에 CONFIRMED된 세부 목적들. */
    val pastConfirmedAtDestination: List<PurposeDetail> = emptyList(),
    /** 출발→도착 목적 규칙(회사 지정 먼저). [PurposeBook.rules]. */
    val routeRules: List<PurposeRouteRule> = emptyList(),
)

/** 규칙 하나. 해당하면 추천, 아니면 null. */
fun interface PurposeRule {
    fun evaluate(ctx: TripContext): Suggestion?
}

/**
 * 규칙 기반 목적 추천 (F-08, 기술명세 3.3). 위에서부터 **첫 번째로 맞는 규칙**을 쓴다.
 *
 * 장소 일치만으로 업무를 확정하지 않는다. 결과는 항상 "추천"이고 사용자가 1탭으로 승인한다.
 */
class PurposeRules(private val zone: ZoneId = SEOUL) {
    private val rules: List<PurposeRule> =
        listOf(
            // (예전 a. 배차 + 목적지 일치 규칙은 뺐다 — 2026-11-15 배차는 누가 쓰는지만 담는다)
            // a2. 목적 규칙: 출발 장소·도착 장소가 둘 다 맞으면 그 목적 (관리자 웹·차 설정에서 정함)
            PurposeRule { ctx ->
                val from = ctx.origin?.placeId ?: return@PurposeRule null
                val to = ctx.destination?.placeId ?: return@PurposeRule null
                ctx.routeRules.firstOrNull { it.originPlaceId == from && it.destPlaceId == to }
                    ?.let { Suggestion(it.purpose, 0.96, PurposeRuleId.ROUTE) }
            },
            // a3. 목적 규칙: 출발지 "어디서든" + 도착 장소가 맞으면 그 목적
            PurposeRule { ctx ->
                val to = ctx.destination?.placeId ?: return@PurposeRule null
                ctx.routeRules.firstOrNull { it.originPlaceId == null && it.destPlaceId == to }
                    ?.let { Suggestion(it.purpose, 0.93, PurposeRuleId.ROUTE_ANY_ORIGIN) }
            },
            // b. 자택 → 회사, 평일 05:00–10:59 → 출근
            PurposeRule { ctx ->
                val t = localTime(ctx.startTime)
                if (ctx.origin?.kind == PlaceKind.HOME &&
                    ctx.destination?.isWorkplace() == true &&
                    t.isWeekday() &&
                    t.hour in 5..10
                ) {
                    Suggestion(PurposeDetail.COMMUTE_IN, 0.95, PurposeRuleId.COMMUTE_IN)
                } else {
                    null
                }
            },
            // c. 회사 → 자택, 16:00–22:59 → 퇴근
            PurposeRule { ctx ->
                val t = localTime(ctx.startTime)
                if (ctx.origin?.isWorkplace() == true && ctx.destination?.kind == PlaceKind.HOME && t.hour in 16..22) {
                    Suggestion(PurposeDetail.COMMUTE_OUT, 0.95, PurposeRuleId.COMMUTE_OUT)
                } else {
                    null
                }
            },
            // d. 목적지가 거래처 → 거래처 방문
            PurposeRule { ctx ->
                if (ctx.destination?.kind ==
                    PlaceKind.CLIENT
                ) {
                    Suggestion(PurposeDetail.CLIENT_VISIT, 0.90, PurposeRuleId.CLIENT)
                } else {
                    null
                }
            },
            // e. 목적지가 지점 → 지점 이동
            PurposeRule { ctx ->
                if (ctx.destination?.kind ==
                    PlaceKind.BRANCH
                ) {
                    Suggestion(PurposeDetail.BRANCH_MOVE, 0.90, PurposeRuleId.BRANCH)
                } else {
                    null
                }
            },
            // f. 같은 목적지에서 과거 CONFIRMED ≥ 3 → 가장 많이 확정한 목적
            PurposeRule { ctx ->
                if (ctx.destination == null ||
                    ctx.pastConfirmedAtDestination.size < PAST_CONFIRMED_MIN
                ) {
                    return@PurposeRule null
                }
                val top = ctx.pastConfirmedAtDestination.groupingBy { it }.eachCount().maxBy { it.value }.key
                Suggestion(top, 0.80, PurposeRuleId.PAST_CONFIRMED)
            },
            // g. 주말 + 미등록 목적지 → 확인 필요(낮은 확신)
            PurposeRule { ctx ->
                if (ctx.destination == null && !localTime(ctx.startTime).isWeekday()) {
                    Suggestion(PurposeDetail.NEEDS_CHECK, 0.30, PurposeRuleId.WEEKEND_UNKNOWN)
                } else {
                    null
                }
            },
            // h. 그 외 → 확인 필요
            PurposeRule { Suggestion(PurposeDetail.NEEDS_CHECK, 0.50, PurposeRuleId.FALLBACK) },
        )

    fun suggest(ctx: TripContext): Suggestion = rules.firstNotNullOf { it.evaluate(ctx) }

    private fun localTime(epochMs: Long): ZonedDateTime = Instant.ofEpochMilli(epochMs).atZone(zone)

    private fun ZonedDateTime.isWeekday() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY

    private fun Place.isWorkplace() = kind == PlaceKind.OFFICE || kind == PlaceKind.BRANCH

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        const val PAST_CONFIRMED_MIN = 3
    }
}
