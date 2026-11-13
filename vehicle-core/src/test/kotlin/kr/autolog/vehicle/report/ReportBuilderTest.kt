package kr.autolog.vehicle.report

import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.ReportBuildResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class ReportBuilderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val nov3 = Instant.parse("2026-11-03T00:00:00Z").toEpochMilli() // 09:00 KST
    private val clock = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC)

    private class Source(val t: List<ReportTrip>, val e: List<ReportExpense> = emptyList()) : ReportSource {
        var saved: Map<String, Set<ReviewFlag>> = emptyMap()

        override suspend fun trips(from: Long, to: Long) = t.filter { it.startTime in from until to }

        override suspend fun expenses(from: Long, to: Long) = e

        override suspend fun schedules(from: Long, to: Long) = emptyList<ReportSchedule>()

        override suspend fun gasStations() = emptySet<String>()

        override suspend fun saveReviewFlags(flags: Map<String, Set<ReviewFlag>>) {
            saved = flags
        }
    }

    private fun trip(id: String, at: Long, km: Double?, p: PurposeDetail?, user: String? = "u-1") = ReportTrip(
        tripId = id, startTime = at, endTime = at + 3_600_000, userId = user, userName = user?.let { "김영업" },
        originLabel = "본사", destinationLabel = "판교", startKm = 100.0, endKm = km?.let { 100.0 + it },
        distanceKm = km, purpose = p,
    )

    private fun builder(src: ReportSource, plate: String? = "12가3456") =
        ReportBuilder(src, { tmp.root }, { plate }, clock)

    @Test
    fun `시트 1 합계와 요약 시트가 같고 미확정은 업무거리에 넣지 않는다`() = runTest {
        val src = Source(
            listOf(
                trip("a", nov3, 23.4, PurposeDetail.CLIENT_VISIT),
                trip("b", nov3 + 86_400_000, 12.8, PurposeDetail.COMMUTE_IN),
                trip("c", nov3 + 2 * 86_400_000, 8.6, PurposeDetail.PERSONAL),
                trip("d", nov3 + 3 * 86_400_000, 5.0, null, user = null),
                trip("e", nov3 + 4 * 86_400_000, null, PurposeDetail.FIELD_WORK),
            ),
            listOf(
                ReportExpense(
                    "x", nov3, 6_800, 4.0, 1_700, 0, "휘발유", "SK 판교", "1234", ExpenseSource.OCR, "h",
                    listOf(
                        "a",
                    ),
                ),
            ),
        )

        val r = builder(src).build(YearMonth.of(2026, 11)) as ReportBuildResult.Built

        val parts = ReviewAndXlsxTest.unzip(r.file.file.readBytes())
        val log = parts.getValue("xl/worksheets/sheet1.xml")
        val summary = parts.getValue("xl/worksheets/sheet3.xml")
        assertEquals(49.8, r.file.totalKm, 1e-9)
        assertEquals("운행기록부_12가3456_202611.xlsx", r.file.file.name)
        assertEquals(5, r.file.tripCount)
        // 합계 행: 총 49.8, 출퇴근 12.8, 일반 업무 23.4 (미확정 5.0·비업무 8.6·거리 미확정은 빠짐)
        listOf("49.8", "12.8", "23.4").forEach { assertTrue(it, "<v>$it</v>" in log && "<v>$it</v>" in summary) }
        assertTrue("목적 미확정 — 업무거리 0" in log)
        assertTrue("운전자 미확정" in log)
        assertTrue("카드 ****1234" in parts.getValue("xl/worksheets/sheet2.xml"))
        assertEquals(3, r.file.reviewCount) // d 운전자 미확정 + 7일 넘은 목적 미확정, e 거리 미확정
        // 이 달 모든 운행을 다시 쓴다: 표시가 있는 것은 d·e, 나머지는 빈 집합(해소 → DB에서 지움)
        assertEquals(setOf("d", "e"), src.saved.filterValues { it.isNotEmpty() }.keys)
        assertEquals(setOf("a", "b", "c", "d", "e"), src.saved.keys)
        assertTrue("해소 방법" in parts.getValue("xl/worksheets/sheet4.xml"))
    }

    @Test
    fun `출발·도착 주소를 따로 적고 고친 거리는 비고에 계기판 값을 남긴다`() = runTest {
        val edited = trip("a", nov3, 25.0, PurposeDetail.CLIENT_VISIT).copy(
            originLabel = null,
            originAddress = "서울 서대문구 연희동 연희로 11-1",
            destinationAddress = "경기 성남시 분당구 삼평동 판교역로 166",
            isDistanceEdited = true,
            odometerDistanceKm = 23.4,
        )
        val filled = trip("b", nov3 + 86_400_000, 12.0, PurposeDetail.CLIENT_VISIT)
            .copy(isDistanceEdited = true, odometerDistanceKm = null)

        val r = builder(Source(listOf(edited, filled))).build(YearMonth.of(2026, 11)) as ReportBuildResult.Built

        val log = ReviewAndXlsxTest.unzip(r.file.file.readBytes()).getValue("xl/worksheets/sheet1.xml")
        assertTrue("출발지 주소" in log && "도착지 주소" in log)
        assertTrue("서울 서대문구 연희동 연희로 11-1 → 판교" in log) // 이름이 없으면 주소
        assertTrue("경기 성남시 분당구 삼평동 판교역로 166" in log)
        assertTrue("거리 수정(계기판 23.4 km)" in log)
        assertTrue("거리 수정(계기판 미확정)" in log)
        assertEquals(37.0, r.file.totalKm, 1e-9)
    }

    @Test
    fun `서버에 연결 전이면 번호판 자리에 미등록을 쓴다`() = runTest {
        val r = builder(Source(emptyList()), plate = null).build(YearMonth.of(2026, 11)) as ReportBuildResult.Built

        assertEquals("미등록", r.file.plateNo)
        assertEquals(0, r.file.tripCount)
    }

    @Test
    fun `200건도 5초 안에 만든다`() = runTest {
        val trips = (0 until 200).map { trip("t-$it", nov3 + it * 3_600_000L, 10.0, PurposeDetail.CLIENT_VISIT) }
        val t0 = System.nanoTime()

        builder(Source(trips)).build(YearMonth.of(2026, 11))

        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
    }
}
