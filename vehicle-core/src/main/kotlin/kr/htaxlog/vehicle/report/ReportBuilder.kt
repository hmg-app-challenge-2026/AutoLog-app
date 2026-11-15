package kr.htaxlog.vehicle.report

import kr.htaxlog.vehicle.core.ExpenseSource
import kr.htaxlog.vehicle.core.PurposeCategory
import kr.htaxlog.vehicle.core.ReportApi
import kr.htaxlog.vehicle.core.ReportBuildResult
import kr.htaxlog.vehicle.core.ReportFile
import kr.htaxlog.vehicle.report.XlsxWriter.Cell
import kr.htaxlog.vehicle.report.XlsxWriter.Look
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 운행기록부 재료. Room 구현은 data/RoomReportSource. */
interface ReportSource {
    suspend fun trips(from: Long, to: Long): List<ReportTrip>

    suspend fun expenses(from: Long, to: Long): List<ReportExpense>

    suspend fun schedules(from: Long, to: Long): List<ReportSchedule>

    suspend fun gasStations(): Set<String>

    /** 계산한 검토 표시를 운행에 남긴다(이력 화면용). */
    suspend fun saveReviewFlags(flags: Map<String, Set<ReviewFlag>>) {}
}

/**
 * 운행기록부 XLSX (F-21) — [ReportApi] 구현. 국세청 업무용승용차 운행기록부 양식 + 검토 열.
 * 시트: ① 운행기록부(별지 서식 배치) ② 비용 ③ 요약 ④ 검토. 사람이 확정한 목적만 업무거리에 넣고, 미확정은 0 + 비고. 셀 수식 없음.
 */
class ReportBuilder(
    private val source: ReportSource,
    private val outputDir: () -> File,
    private val plateNo: () -> String?,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    private val writer: XlsxWriter = XlsxWriter(),
    private val log: (String) -> Unit = {},
    /** 별지 서식 ①차종. 모르면 빈칸. */
    private val vehicleModel: () -> String? = { null },
) : ReportApi {
    override suspend fun build(month: YearMonth): ReportBuildResult = try {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val trips = source.trips(from, to).sortedBy { it.startTime }
        val expenses = source.expenses(from, to).sortedBy { it.occurredAt }
        // 배차는 이 달에 시작한 것만 본다(겹치기만 한 지난달 배차를 두 번 세지 않게).
        // 맞춰 볼 운행은 경계 앞뒤까지 읽는다: 11/30 23시 배차를 12/1 0시 반에 운행해도 일치로 본다.
        val margin = ReviewCalculator.SCHEDULE_WINDOW_MS
        val review = ReviewCalculator.calculate(
            trips,
            expenses,
            source.schedules(from, to).filter { it.startsAt in from until to },
            source.gasStations(),
            clock.millis(),
            scheduleTrips = source.trips(from - margin - LONG_TRIP_MS, to + margin),
        )
        // 이 달 모든 운행의 표시를 다시 쓴다. 해소된 운행은 빈 집합 → DB에서 지워진다.
        source.saveReviewFlags(trips.associate { it.tripId to review.tripFlags[it.tripId].orEmpty() })
        val summary = Summary.of(trips, expenses, review)
        val plate = plateNo() ?: UNREGISTERED_PLATE
        val dir = outputDir().apply { mkdirs() }
        val file = File(dir, "운행기록부_${plate.replace(Regex("[\\s/\\\\:]"), "")}_${month.format(YYYYMM)}.xlsx")
        file.outputStream().use { out ->
            writer.write(
                listOf(
                    logSheet(month, plate, trips, review, summary),
                    expenseSheet(expenses, trips, review),
                    summarySheet(month, plate, summary),
                    reviewSheet(review),
                ),
                out,
            )
        }
        log("report ${file.name}: ${trips.size} trips, ${review.count} reviews")
        ReportBuildResult.Built(ReportFile(month, file, plate, trips.size, summary.totalKm, review.count))
    } catch (e: IOException) {
        log("report failed: ${e.message}")
        ReportBuildResult.Failed("운행기록부를 저장하지 못했어요. 저장 공간을 확인해 주세요")
    }

    /** 시트 1과 시트 3이 같은 숫자를 쓰도록 한 번만 계산한다(F-21 수용 기준 "시트 1 합계 = 시트 3"). */
    data class Summary(
        val tripCount: Int,
        val totalKm: Double,
        val commuteKm: Double,
        val businessKm: Double,
        val nonBusinessKm: Double,
        val undeterminedKm: Double,
        val expenseWon: Int,
        val liters: Double,
        val reviewCount: Int,
    ) {
        /** 업무사용비율(%) = (출퇴근 + 일반 업무) ÷ 총 주행거리. 국세청 양식 기준. */
        val businessRatio: Double get() = if (totalKm > 0) (commuteKm + businessKm) / totalKm * 100 else 0.0

        companion object {
            fun of(trips: List<ReportTrip>, expenses: List<ReportExpense>, review: ReviewResult): Summary {
                fun km(c: PurposeCategory?) = sumKm(trips.filter { it.purpose?.category == c }.map { it.distanceKm })
                return Summary(
                    tripCount = trips.size,
                    totalKm = sumKm(trips.map { it.distanceKm }),
                    commuteKm = km(PurposeCategory.COMMUTE),
                    businessKm = km(PurposeCategory.BUSINESS),
                    nonBusinessKm = km(PurposeCategory.NON_BUSINESS),
                    undeterminedKm = km(null) + km(PurposeCategory.UNDETERMINED),
                    expenseWon = expenses.sumOf { it.amountWon },
                    liters = sumKm(expenses.map { it.liters }),
                    reviewCount = review.count,
                )
            }
        }
    }

    /**
     * 국세청 「업무용승용차 운행기록부에 관한 별지 서식」(2022.4.1. 개정) 배치 그대로 그린다: 머리(과세기간·상호명·사업자등록번호),
     * 1. 기본정보(①차종 ②자동차등록번호), 2. 업무용 사용비율 계산(③~⑩ 운행 줄, ⑪⑫⑬ 합계). 목적·경로는 ⑩비고에 적는다.
     * 양식 오른쪽(한 칸 띄움)에 양식 외 참고 열(출발지·도착지 주소, 검토)을 둔다. 상호명·사업자등록번호는 앱이 모르므로 빈칸.
     */
    private fun logSheet(
        month: YearMonth,
        plate: String,
        trips: List<ReportTrip>,
        review: ReviewResult,
        s: Summary,
    ): XlsxWriter.Sheet {
        val g = Grid()
        val head = Look(border = true, center = true, wrap = true)
        val box = Look(border = true)
        val mid = Look(border = true, center = true)
        g.put(0, 0, Cell.Text("【업무용승용차 운행기록부에 관한 별지 서식】<2022.4.1. 개정>", look = Look(size = 9)))
        // 머리: 과세기간 | 기간 | 제목 | 상호명·사업자등록번호
        g.box("A3:A4", Cell.Text("과 세 기 간", look = head))
        g.box(
            "B3:C4",
            Cell.Text("${month.atDay(1).format(DOT_DATE)} ~ ${month.atEndOfMonth().format(DOT_DATE)}", look = head),
        )
        g.box("D3:G4", Cell.Text("업무용승용차 운행기록부", bold = true, look = head.copy(size = 18)))
        g.box("H3:H3", Cell.Text("상 호 명", look = head))
        g.box("I3:I3", Cell.Text("", look = box))
        g.box("H4:H4", Cell.Text("사업자등록번호", look = head))
        g.box("I4:I4", Cell.Text("", look = box))
        // 1. 기본정보
        g.put(5, 0, Cell.Text("1. 기본정보", bold = true))
        g.box("A7:C7", Cell.Text("①차 종", look = head))
        g.box("D7:F7", Cell.Text("②자동차등록번호", look = head))
        g.box("A8:C8", Cell.Text(vehicleModel().orEmpty(), look = mid))
        g.box("D8:F8", Cell.Text(plate, look = mid))
        // 2. 업무용 사용비율 계산 — 머리 3줄
        g.put(9, 0, Cell.Text("2. 업무용 사용비율 계산", bold = true))
        g.box("A11:A13", Cell.Text("③사용\n일자\n(요일)", look = head))
        g.box("B11:C11", Cell.Text("④사용자", look = head))
        g.box("D11:I11", Cell.Text("운 행 내 역", look = head))
        g.box("B12:B13", Cell.Text("부서", look = head))
        g.box("C12:C13", Cell.Text("성명", look = head))
        g.box("D12:D13", Cell.Text("⑤주행 전\n계기판의 거리(km)", look = head))
        g.box("E12:E13", Cell.Text("⑥주행 후\n계기판의 거리(km)", look = head))
        g.box("F12:F13", Cell.Text("⑦주행거리(km)", look = head))
        g.box("G12:H12", Cell.Text("업무용 사용거리(km)", look = head))
        g.box("G13:G13", Cell.Text("⑧출·퇴근용(km)", look = head))
        g.box("H13:H13", Cell.Text("⑨일반 업무용(km)", look = head))
        g.box("I12:I13", Cell.Text("⑩비 고", look = head))
        g.box("K11:M11", Cell.Text("참고 (양식 외)", look = head))
        g.box("K12:K13", Cell.Text("출발지 주소", look = head))
        g.box("L12:L13", Cell.Text("도착지 주소", look = head))
        g.box("M12:M13", Cell.Text("검토", look = head))
        // 운행 줄
        var r = FIRST_TRIP_ROW
        (if (trips.isEmpty()) listOf(null) else trips).forEach { t ->
            val cat = t?.purpose?.category
            listOf(
                t?.let { Cell.Text("${date(it.startTime)}(${dayOfWeek(it.startTime)})", look = mid) },
                t?.dept?.let { Cell.Text(it, look = mid) },
                t?.let { Cell.Text(it.userName ?: "미확정", look = mid) },
                t?.startKm?.let { Cell.Num(it, look = box) },
                t?.endKm?.let { Cell.Num(it, look = box) },
                t?.distanceKm?.let { Cell.Num(it, look = box) },
                t?.distanceKm?.takeIf { cat == PurposeCategory.COMMUTE }?.let { Cell.Num(it, look = box) },
                t?.distanceKm?.takeIf { cat == PurposeCategory.BUSINESS }?.let { Cell.Num(it, look = box) },
                t?.let { Cell.Text(remarkColumn(it), look = box) },
            ).forEachIndexed { c, cell -> g.put(r, c, cell ?: Cell.Text("", look = box)) }
            listOf(
                t?.originAddress,
                t?.destinationAddress,
                t?.let {
                    review.tripFlags[it.tripId]?.joinToString(", ") { f -> f.labelKo }
                },
            )
                .forEachIndexed { i, v -> g.put(r, REF_COL + i, Cell.Text(v.orEmpty(), look = box)) }
            r++
        }
        // ⑪⑫⑬ 합계
        val a = r + 1
        g.box("A$a:C${a + 1}", Cell.Text("", look = box.copy(gray = true)))
        g.box("D$a:F$a", Cell.Text("⑪과세기간 총주행 거리(km)", look = head))
        g.box("G$a:H$a", Cell.Text("⑫과세기간 업무용 사용거리(km)", look = head))
        g.box("I$a:I$a", Cell.Text("⑬업무사용비율(%)(⑫/⑪)", look = head))
        g.box("D${a + 1}:F${a + 1}", Cell.Num(s.totalKm, bold = true, look = mid))
        g.box("G${a + 1}:H${a + 1}", Cell.Num(sumKm(listOf(s.commuteKm, s.businessKm)), bold = true, look = mid))
        g.box("I${a + 1}:I${a + 1}", Cell.Num(s.businessRatio, bold = true, look = mid))
        return XlsxWriter.Sheet(
            name = "운행기록부",
            rows = g.rows,
            widths = listOf(15, 11, 10, 15, 15, 13, 14, 14, 46, 2, 34, 34, 26),
            merges = g.merges,
            heights = mapOf(2 to 24.0, 3 to 24.0, 6 to 22.0, 7 to 22.0, 10 to 22.0, 11 to 22.0, 12 to 30.0),
            landscape = true,
        )
    }

    /** 병합 영역마다 모든 칸에 테두리를 깔아야 엑셀이 선을 그린다. */
    private class Grid {
        val rows = mutableListOf<MutableList<Cell?>>()
        val merges = mutableListOf<String>()

        fun put(row: Int, col: Int, cell: Cell) {
            while (rows.size <= row) rows += mutableListOf<Cell?>()
            val line = rows[row]
            while (line.size <= col) line += null
            line[col] = cell
        }

        /** "A3:C4" 영역에 [cell]을 왼쪽 위에, 나머지는 같은 모양의 빈칸으로 채우고 병합한다. 한 칸이면 병합하지 않는다. */
        fun box(range: String, cell: Cell) {
            val (from, to) = range.split(":").map { parse(it) }
            for (r in from.first..to.first) {
                for (c in from.second..to.second) {
                    put(
                        r,
                        c,
                        if (r == from.first &&
                            c == from.second
                        ) {
                            cell
                        } else {
                            Cell.Text("", look = cell.look)
                        },
                    )
                }
            }
            if (from != to) merges += range
        }

        private fun parse(ref: String): Pair<Int, Int> {
            val letters = ref.takeWhile { it.isLetter() }
            val col = letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
            return ref.drop(letters.length).toInt() - 1 to col
        }
    }

    /** ⑩비고: 목적 · 출발 → 도착 · 그 밖의 메모. */
    private fun remarkColumn(t: ReportTrip): String =
        listOf(t.purpose?.labelKo ?: "미확정", route(t), remark(t)).filter { it.isNotBlank() }.joinToString(" · ")

    private fun expenseSheet(
        expenses: List<ReportExpense>,
        trips: List<ReportTrip>,
        review: ReviewResult,
    ): XlsxWriter.Sheet {
        val tripById = trips.associateBy { it.tripId }
        return XlsxWriter.Sheet(
            name = "비용",
            rows = buildList {
                add(EXPENSE_HEADER.map { Cell.Text(it, bold = true) })
                expenses.forEach { e ->
                    add(
                        listOf(
                            Cell.Text(date(e.occurredAt)),
                            Cell.Text("주유"),
                            text(e.merchant),
                            text(e.fuelType),
                            num(e.liters, 2),
                            num(e.unitPrice?.toDouble(), 0),
                            Cell.Num(e.discountWon.toDouble(), 0),
                            Cell.Num(e.amountWon.toDouble(), 0),
                            text(e.cardLast4?.let { "카드 ****$it" }),
                            text(
                                e.tripIds.mapNotNull {
                                    tripById[it]
                                }.joinToString(", ") { "${date(it.startTime)} ${route(it)}" },
                            ),
                            Cell.Text(EVIDENCE.getValue(e.source)),
                            text(review.expenseFlags[e.expenseId]?.joinToString(", ") { it.labelKo }),
                        ),
                    )
                }
                add(
                    listOf(Cell.Text("합계", bold = true)) + List(6) { null } +
                        Cell.Num(expenses.sumOf { it.amountWon }.toDouble(), 0, bold = true),
                )
            },
            widths = listOf(12, 6, 18, 8, 10, 10, 10, 12, 14, 34, 11, 16),
        )
    }

    private fun summarySheet(month: YearMonth, plate: String, s: Summary) = XlsxWriter.Sheet(
        name = "요약",
        rows = listOf(
            listOf(Cell.Text("${month.format(YYYY_MM)} 운행 요약 · $plate", bold = true)),
            listOf(Cell.Text("운행 건수"), Cell.Num(s.tripCount.toDouble(), 0)),
            listOf(Cell.Text("총 주행거리(km)"), Cell.Num(s.totalKm)),
            listOf(Cell.Text("출·퇴근용(km)"), Cell.Num(s.commuteKm)),
            listOf(Cell.Text("일반 업무용(km)"), Cell.Num(s.businessKm)),
            listOf(Cell.Text("비업무용(km)"), Cell.Num(s.nonBusinessKm)),
            listOf(Cell.Text("미확정(km)"), Cell.Num(s.undeterminedKm)),
            listOf(Cell.Text("업무사용비율(%)"), Cell.Num(s.businessRatio)),
            listOf(Cell.Text("주유 비용 합계(원)"), Cell.Num(s.expenseWon.toDouble(), 0)),
            listOf(Cell.Text("주유량(L)"), Cell.Num(s.liters, 2)),
            listOf(Cell.Text("검토 필요(건)"), Cell.Num(s.reviewCount.toDouble(), 0)),
        ),
        widths = listOf(22, 14),
    )

    private fun reviewSheet(review: ReviewResult) = XlsxWriter.Sheet(
        name = "검토",
        rows = listOf(listOf("구분", "일시", "대상", "해소 방법").map { Cell.Text(it, bold = true) }) +
            review.issues.map { i ->
                listOf(
                    Cell.Text(i.flag.labelKo),
                    Cell.Text(dateTime(i.at)),
                    Cell.Text(i.target),
                    Cell.Text(i.flag.resolution),
                )
            },
        widths = listOf(18, 18, 32, 70),
    )

    private fun remark(t: ReportTrip): String = listOfNotNull(
        t.note,
        "앱 미실행 중 운행(시각 추정)".takeIf { t.timeEstimated && t.note?.contains("앱 미실행") != true },
        "목적 미확정 — 업무거리 0".takeIf { t.purpose == null || t.purpose.category == PurposeCategory.UNDETERMINED },
        "운전자 미확정".takeIf { t.userId == null },
        // 사람이 고친 거리도 일반 기록과 똑같이 적는다(2026-11-15 사용자 결정). 계기판 원본은 Room에 그대로 남는다(F-05)
    ).joinToString(" · ")

    /** 이름(등록 장소·내비 목적지·건물) → 이름. 이름이 없으면 주소. 주소는 옆 칸에 따로 적는다. */
    private fun route(t: ReportTrip) =
        listOfNotNull(t.originLabel ?: t.originAddress, t.destinationLabel ?: t.destinationAddress).joinToString(" → ")

    private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(DATE)

    private fun dateTime(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(DATE_TIME)

    private fun dayOfWeek(ms: Long) =
        Instant.ofEpochMilli(ms).atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    private fun text(v: String?) = v?.takeIf { it.isNotBlank() }?.let { Cell.Text(it) }

    private fun num(v: Double?, decimals: Int = 1) = v?.let { Cell.Num(it, decimals) }

    companion object {
        const val UNREGISTERED_PLATE = "미등록"

        /** 배차 일치 판단 때 앞쪽으로 더 읽는 시간(달 시작 전에 출발한 긴 운행). */
        const val LONG_TRIP_MS = 24 * 3_600_000L

        /** 10진수 그대로 더한다. Double로 더하면 23.4 + 12.8 + 8.6 + 5.0 = 49.800000000000004가 된다. */
        fun sumKm(values: List<Double?>): Double = values.filterNotNull().fold(java.math.BigDecimal.ZERO) { acc, v ->
            acc + java.math.BigDecimal(v.toString())
        }.toDouble()
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val YYYYMM = DateTimeFormatter.ofPattern("yyyyMM")
        private val YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM")
        private val DOT_DATE = DateTimeFormatter.ofPattern("yyyy. M. d.")

        /** 별지 서식에서 운행 줄이 시작하는 행(0-기반, 엑셀 14행). 머리 3줄 바로 아래. */
        private const val FIRST_TRIP_ROW = 13

        /** 양식 외 참고 열 시작(K). I(⑩비고)와 한 칸 띄운다. */
        private const val REF_COL = 10
        val EXPENSE_HEADER = listOf(
            "일자", "구분", "상호", "유종", "수량(L)", "단가(원)", "할인(원)", "금액(원)", "결제수단", "연결 운행", "증빙", "검토",
        )
        private val EVIDENCE = mapOf(
            ExpenseSource.OCR to "영수증",
            ExpenseSource.VOICE to "음성 입력",
            ExpenseSource.TAP to "직접 입력",
        )
    }
}
