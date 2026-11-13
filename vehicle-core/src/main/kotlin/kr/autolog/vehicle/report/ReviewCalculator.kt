package kr.autolog.vehicle.report

import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.PurposeDetail

// 운행기록부 계산용 값 (Room·Android 의존 없음). ReportBuilder가 Room에서 옮겨 담는다.

data class ReportTrip(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val userId: String?,
    val userName: String? = null,
    val dept: String? = null,
    val originLabel: String? = null,
    val destinationLabel: String? = null,
    val startKm: Double? = null,
    val endKm: Double? = null,
    /** 보고 거리 — 사람이 고친 값이 있으면 그것, 없으면 계기판 차이. null = 거리 미확정. */
    val distanceKm: Double? = null,
    /** 사람이 확정한 목적. null = 미확정. */
    val purpose: PurposeDetail? = null,
    val note: String? = null,
    /** 시각이 추정인 운행(앱 미실행 중). */
    val timeEstimated: Boolean = false,
    val stopLabels: List<String> = emptyList(),
    /** 출발·도착 주소(`광역 기초 읍면동 도로명 번호`). 모르면 null. */
    val originAddress: String? = null,
    val destinationAddress: String? = null,
    /** [distanceKm]가 사람이 고친 값이면 계기판 차이 원본(미확정이면 null). 고치지 않았으면 [isDistanceEdited] = false. */
    val isDistanceEdited: Boolean = false,
    val odometerDistanceKm: Double? = null,
)

data class ReportExpense(
    val expenseId: String,
    val occurredAt: Long,
    val amountWon: Int,
    val liters: Double?,
    val unitPrice: Int?,
    val discountWon: Int,
    val fuelType: String?,
    val merchant: String?,
    val cardLast4: String?,
    val source: ExpenseSource,
    val receiptSha256: String?,
    val tripIds: List<String> = emptyList(),
)

data class ReportSchedule(val scheduleId: String, val userName: String?, val startsAt: Long, val endsAt: Long)

/** 검토 표시 7종 (F-23). 엑셀 "검토" 열에 [labelKo], 검토 시트에 [resolution]. 해소는 차 안에서 한다. */
enum class ReviewFlag(val labelKo: String, val resolution: String) {
    PURPOSE_UNSET("목적 미확정", "차 안 [운행 이력]에서 이 운행의 목적을 확정하세요"),
    DRIVER_UNSET("운전자 미확정", "다음 운전자 선택 화면에서 이 운행의 운전자를 고르세요"),
    DISTANCE_UNSET("거리 미확정", "계기판을 읽지 못한 운행입니다. 실제 거리를 확인해 고치세요"),
    SCHEDULE_MISMATCH("일정–운행 불일치", "배차 시간 앞뒤 3시간에 운행이 없습니다. 배차 취소 여부를 확인하세요"),
    NO_RECEIPT("증빙 없음", "주유소에 들렀지만 비용이 없습니다. 영수증을 찍어 올리거나 주유 기록을 남기세요"),
    DUPLICATE_RECEIPT("중복 영수증", "같은 영수증이 두 번 등록됐습니다. 하나를 지우세요"),
    FUEL_OUTLIER("연료 이상치", "이달 주유량이 주행거리에 비해 많습니다. 비용 내역을 확인하세요"),
}

/** 검토 시트 한 줄. */
data class ReviewIssue(val flag: ReviewFlag, val at: Long, val target: String)

data class ReviewResult(
    val tripFlags: Map<String, Set<ReviewFlag>>,
    val expenseFlags: Map<String, Set<ReviewFlag>>,
    val issues: List<ReviewIssue>,
) {
    val count: Int get() = issues.size
}

/**
 * 검토 표시 계산 (F-23, 기술명세 3.4). 순수 함수. 엑셀 생성 직전에 돈다.
 * 원칙: "확인 필요"만 표시하고 값을 바꾸지 않는다. 근거가 있을 때만 표시한다(오탐 0).
 */
object ReviewCalculator {
    const val PENDING_DAYS_MS = 7 * 24 * 3_600_000L
    const val SCHEDULE_WINDOW_MS = 3 * 3_600_000L

    /** 차량 평균 연비 [가정]. 월 L/km가 이 역수의 2배를 넘으면 이상치. */
    const val DEFAULT_KM_PER_LITER = 10.0

    fun calculate(
        trips: List<ReportTrip>,
        expenses: List<ReportExpense>,
        schedules: List<ReportSchedule>,
        gasStations: Set<String>,
        now: Long,
        kmPerLiter: Double = DEFAULT_KM_PER_LITER,
        /** 배차와 맞춰 볼 운행. 월 경계 앞뒤 운행까지 넣어야 경계 배차가 오탐되지 않는다. 기본은 [trips]. */
        scheduleTrips: List<ReportTrip> = trips,
    ): ReviewResult {
        val tripFlags = mutableMapOf<String, MutableSet<ReviewFlag>>()
        val expenseFlags = mutableMapOf<String, MutableSet<ReviewFlag>>()
        val issues = mutableListOf<ReviewIssue>()
        fun flagTrip(t: ReportTrip, f: ReviewFlag) {
            if (tripFlags.getOrPut(t.tripId) { mutableSetOf() }.add(f)) {
                issues +=
                    ReviewIssue(f, t.startTime, tripLabel(t))
            }
        }

        val linkedTrips = expenses.flatMap { it.tripIds }.toSet()
        for (t in trips) {
            if (t.purpose == null &&
                t.endTime != null &&
                now - t.endTime >= PENDING_DAYS_MS
            ) {
                flagTrip(t, ReviewFlag.PURPOSE_UNSET)
            }
            if (t.userId == null) flagTrip(t, ReviewFlag.DRIVER_UNSET)
            if (t.distanceKm == null) flagTrip(t, ReviewFlag.DISTANCE_UNSET)
            if (t.stopLabels.any { it in gasStations } && t.tripId !in linkedTrips) flagTrip(t, ReviewFlag.NO_RECEIPT)
        }

        for (s in schedules.filter { it.endsAt <= now }) {
            // 배차 시간 [시작−3h, 종료+3h]와 겹치는 운행이 하나라도 있으면 일치(종일 배차 중간 운행 포함)
            val matched = scheduleTrips.any { t ->
                val tEnd = t.endTime ?: t.startTime
                t.startTime <= s.endsAt + SCHEDULE_WINDOW_MS && tEnd >= s.startsAt - SCHEDULE_WINDOW_MS
            }
            if (!matched) {
                issues +=
                    ReviewIssue(ReviewFlag.SCHEDULE_MISMATCH, s.startsAt, "배차 ${s.userName ?: ""}".trim())
            }
        }

        expenses.filter {
            it.receiptSha256 != null
        }.groupBy { it.receiptSha256 }.values.filter { it.size > 1 }.forEach { dup ->
            dup.forEach { e ->
                expenseFlags.getOrPut(e.expenseId) { mutableSetOf() } += ReviewFlag.DUPLICATE_RECEIPT
                issues += ReviewIssue(ReviewFlag.DUPLICATE_RECEIPT, e.occurredAt, expenseLabel(e))
            }
        }

        // 연비는 "주유 사이"로 본다: 달의 마지막 주유는 다음 달 주행분이라 뺀다(월말 가득 주유 오탐 방지).
        // 거리 미확정 운행이 있으면 km가 적게 잡혀 오탐이 나므로 판단하지 않는다.
        val fills = expenses.filter { (it.liters ?: 0.0) > 0 }.sortedBy { it.occurredAt }
        val liters = fills.dropLast(1).sumOf { it.liters ?: 0.0 }
        val km = trips.sumOf { it.distanceKm ?: 0.0 }
        val distanceKnown = trips.none { it.distanceKm == null }
        if (distanceKnown && fills.size >= 2 && liters > 0 && km > 0 && liters / km > 2.0 / kmPerLiter) {
            expenses.filter { (it.liters ?: 0.0) > 0 }.forEach {
                expenseFlags.getOrPut(it.expenseId) { mutableSetOf() } +=
                    ReviewFlag.FUEL_OUTLIER
            }
            issues +=
                ReviewIssue(
                    ReviewFlag.FUEL_OUTLIER,
                    expenses.minOf {
                        it.occurredAt
                    },
                    "이달 %.1f L / %.1f km".format(liters, km),
                )
        }

        return ReviewResult(tripFlags, expenseFlags, issues.sortedBy { it.at })
    }

    private fun tripLabel(t: ReportTrip) =
        listOfNotNull(t.originLabel, t.destinationLabel).joinToString(" → ").ifBlank { "운행" }

    private fun expenseLabel(e: ReportExpense) = listOfNotNull(e.merchant, "%,d원".format(e.amountWon)).joinToString(" ")
}
