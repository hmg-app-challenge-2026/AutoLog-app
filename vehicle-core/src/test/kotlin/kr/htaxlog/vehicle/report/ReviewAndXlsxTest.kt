package kr.htaxlog.vehicle.report

import kr.htaxlog.vehicle.core.ExpenseSource
import kr.htaxlog.vehicle.core.PurposeDetail
import kr.htaxlog.vehicle.report.XlsxWriter.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ReviewAndXlsxTest {
    private val day = 24 * 3_600_000L
    private val now = 30 * day

    private fun trip(id: String, start: Long, purpose: PurposeDetail? = PurposeDetail.CLIENT_VISIT) = ReportTrip(
        tripId = id, startTime = start, endTime = start + 3_600_000, userId = "u-1", originLabel = "본사",
        destinationLabel = "판교", startKm = 100.0, endKm = 123.4, distanceKm = 23.4, purpose = purpose,
    )

    private fun fuel(id: String, liters: Double, sha: String? = id, tripIds: List<String> = emptyList()) =
        ReportExpense(
            expenseId = id, occurredAt = 2 * day, amountWon = 68_000, liters = liters, unitPrice = 1_700,
            discountWon = 0,
            fuelType = "휘발유", merchant = "SK 판교", cardLast4 = "1234", source = ExpenseSource.OCR, receiptSha256 = sha,
            tripIds = tripIds,
        )

    private fun calc(
        trips: List<ReportTrip>,
        expenses: List<ReportExpense> = emptyList(),
        schedules: List<ReportSchedule> = emptyList(),
    ) = ReviewCalculator.calculate(trips, expenses, schedules, setOf("SK 판교주유소"), now)

    @Test
    fun `깨끗한 한 달은 검토가 하나도 없다(오탐 0)`() {
        val r = calc(
            trips = listOf(trip("t-1", 2 * day).copy(stopLabels = listOf("SK 판교주유소"))),
            expenses = listOf(fuel("e-1", 2.0, tripIds = listOf("t-1"))),
            schedules = listOf(ReportSchedule("s-1", "김영업", 2 * day + 3_600_000, 2 * day + 7_200_000)),
        )

        assertEquals(0, r.count)
    }

    @Test
    fun `7일 넘게 목적이 없으면 목적 미확정, 7일 안이면 아직 아니다`() {
        val r = calc(listOf(trip("old", 1 * day, purpose = null), trip("new", 25 * day, purpose = null)))

        assertEquals(setOf(ReviewFlag.PURPOSE_UNSET), r.tripFlags["old"])
        assertFalse("new" in r.tripFlags)
    }

    @Test
    fun `운전자가 없으면 운전자 미확정이다`() {
        assertEquals(setOf(ReviewFlag.DRIVER_UNSET), calc(listOf(trip("t", day).copy(userId = null))).tripFlags["t"])
    }

    @Test
    fun `거리를 못 읽은 운행은 거리 미확정이다`() {
        assertEquals(
            setOf(ReviewFlag.DISTANCE_UNSET),
            calc(listOf(trip("t", day).copy(distanceKm = null))).tripFlags["t"],
        )
    }

    @Test
    fun `배차 앞뒤 3시간에 운행이 없으면 일정–운행 불일치다`() {
        val r =
            calc(listOf(trip("t", day)), schedules = listOf(ReportSchedule("s", "김영업", 5 * day, 5 * day + 3_600_000)))

        assertEquals(ReviewFlag.SCHEDULE_MISMATCH, r.issues.single().flag)
    }

    @Test
    fun `주유소에 들렀는데 연결된 비용이 없으면 증빙 없음이다`() {
        val r = calc(listOf(trip("t", day).copy(stopLabels = listOf("SK 판교주유소"))))

        assertEquals(setOf(ReviewFlag.NO_RECEIPT), r.tripFlags["t"])
    }

    @Test
    fun `같은 영수증 해시가 두 건이면 둘 다 중복 영수증이다`() {
        val r = calc(listOf(trip("t", day)), listOf(fuel("e-1", 2.0, "same"), fuel("e-2", 2.0, "same")))

        assertEquals(setOf(ReviewFlag.DUPLICATE_RECEIPT), r.expenseFlags["e-1"])
        assertEquals(setOf(ReviewFlag.DUPLICATE_RECEIPT), r.expenseFlags["e-2"])
    }

    @Test
    fun `이달 주유량이 평균 연비 역수의 2배를 넘으면 연료 이상치다`() {
        // 마지막 주유(e-2)는 다음 달 몫이라 빼고, 23.4 km에 10 L = 0.43 L/km > 2 / 10 km/L = 0.2 L/km
        val r = calc(listOf(trip("t", day)), listOf(fuel("e-1", 10.0), fuel("e-2", 40.0).copy(occurredAt = 20 * day)))

        assertTrue(ReviewFlag.FUEL_OUTLIER in r.expenseFlags["e-1"]!!)
    }

    @Test
    fun `월말에 한 번 가득 주유한 것은 연료 이상치가 아니다`() {
        val r = calc(listOf(trip("t", day)), listOf(fuel("e-1", 50.0).copy(occurredAt = 29 * day)))

        assertTrue(r.expenseFlags["e-1"].orEmpty().isEmpty())
    }

    @Test
    fun `거리 미확정 운행이 있으면 연비를 판단하지 않는다`() {
        val trips = listOf(trip("t", day), trip("u", 3 * day).copy(distanceKm = null))
        val r = calc(trips, listOf(fuel("e-1", 10.0), fuel("e-2", 10.0).copy(occurredAt = 20 * day)))

        assertFalse(r.issues.any { it.flag == ReviewFlag.FUEL_OUTLIER })
    }

    @Test
    fun `종일 배차 중간에 운행이 있으면 불일치가 아니다`() {
        // 09:00–18:00 배차, 13:00 출발 운행 (시작 ±3h 밖이지만 배차 시간 안)
        val start = 5 * day + 9 * 3_600_000
        val r = calc(
            listOf(trip("t", start + 4 * 3_600_000)),
            schedules = listOf(ReportSchedule("s", "김영업", start, start + 9 * 3_600_000)),
        )

        assertFalse(r.issues.any { it.flag == ReviewFlag.SCHEDULE_MISMATCH })
    }

    @Test
    fun `월말 배차를 다음 달 첫 운행이 채우면 불일치가 아니다`() {
        // 이 달 운행은 없고, 경계 다음(scheduleTrips)에 30분 뒤 출발한 운행만 있다
        val s = ReportSchedule("s", "김영업", 28 * day + 23 * 3_600_000, 28 * day + 24 * 3_600_000)
        val next = trip("next", 29 * day + 30 * 60_000)
        val r = ReviewCalculator.calculate(
            emptyList(),
            emptyList(),
            listOf(s),
            emptySet(),
            now,
            scheduleTrips = listOf(next),
        )

        assertTrue(r.issues.isEmpty())
    }

    @Test
    fun `XLSX는 올바른 Zip·XML이고 값과 시트 이름이 들어간다`() {
        val out = ByteArrayOutputStream()
        XlsxWriter().write(
            listOf(
                XlsxWriter.Sheet(
                    "운행기록부",
                    listOf(listOf(Cell.Text("구분 <&>", bold = true), Cell.Num(23.4))),
                    widths = listOf(12, 8),
                ),
                XlsxWriter.Sheet("요약", listOf(listOf(Cell.Num(68_000.0, decimals = 0)))),
            ),
            out,
        )

        val parts = unzip(out.toByteArray())
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        parts.values.forEach { parser.parse(ByteArrayInputStream(it.toByteArray())) } // 모든 XML이 잘 짜였는지
        assertTrue("xl/worksheets/sheet2.xml" in parts)
        assertTrue("name=\"운행기록부\"" in parts["xl/workbook.xml"]!!)
        assertTrue("구분 &lt;&amp;&gt;" in parts["xl/worksheets/sheet1.xml"]!!)
        assertTrue("<v>23.4</v>" in parts["xl/worksheets/sheet1.xml"]!!)
    }

    @Test
    fun `열 번호는 엑셀 표기로 바뀐다`() {
        assertEquals("A1", XlsxWriter.ref(0, 0))
        assertEquals("Z3", XlsxWriter.ref(25, 2))
        assertEquals("AA10", XlsxWriter.ref(26, 9))
    }

    companion object {
        fun unzip(bytes: ByteArray): Map<String, String> {
            val m = mutableMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                generateSequence { z.nextEntry }.forEach { e -> m[e.name] = z.readBytes().toString(Charsets.UTF_8) }
            }
            return m
        }
    }
}
