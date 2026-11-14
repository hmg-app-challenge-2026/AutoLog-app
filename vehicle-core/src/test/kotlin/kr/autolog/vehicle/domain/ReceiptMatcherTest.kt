package kr.autolog.vehicle.domain

import kr.autolog.shared.OcrStatus
import kr.autolog.shared.PlaceKind
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptMatcherTest {
    private val station = Place("gs", "SK 판교주유소", PlaceKind.GAS_STATION, 37.40, 127.10)

    private fun trip(
        id: String,
        start: Long,
        end: Long?,
        stops: List<String> = emptyList(),
        endAt: Pair<Double, Double>? = null,
    ) = TripSummary(
        tripId = id,
        vehicleId = "car-1",
        userId = null,
        startTime = start,
        endTime = end,
        originLabel = "본사",
        destinationLabel = "거래처",
        endLat = endAt?.first,
        endLng = endAt?.second,
        stopLabels = stops,
    )

    @Test
    fun `주유소에서 정차한 운행이 시각이 더 먼 운행보다 먼저 뽑힌다`() {
        val near = trip("near", kst(9), kst(9, 50))
        val fuel = trip("fuel", kst(10, 30), kst(11), stops = listOf("SK 판교주유소"))

        val c = ReceiptMatcher.candidates(kst(9, 40), listOf(near, fuel), listOf(station))

        assertEquals(listOf("fuel", "near"), c.map { it.tripId })
        assertTrue(c[0].hasFuelStop)
    }

    @Test
    fun `도착 좌표가 등록 주유소면 주유 정차로 본다`() {
        val c = ReceiptMatcher.candidates(
            kst(9),
            listOf(trip("t", kst(8), kst(8, 30), endAt = 37.40 to 127.10)),
            listOf(station),
        )

        assertTrue(c.single().hasFuelStop)
    }

    @Test
    fun `2시간 밖의 운행은 후보가 아니다`() {
        val c = ReceiptMatcher.candidates(kst(12), listOf(trip("old", kst(8), kst(9, 59))), emptyList())

        assertTrue(c.isEmpty())
    }

    @Test
    fun `후보는 가까운 3개를 뽑아 최근 출발이 위로 온다`() {
        val trips = (0..4).map { trip("t$it", kst(8) + it * 20 * MINUTE, kst(8) + it * 20 * MINUTE + 10 * MINUTE) }

        val c = ReceiptMatcher.candidates(kst(8, 45), trips, emptyList())

        assertEquals(listOf("t3", "t2", "t1"), c.map { it.tripId })
    }

    @Test
    fun `지금 기록 중인 운행은 시각이 멀어도 후보에 들어가고 맨 위다`() {
        val done = (0..2).map { trip("t$it", kst(8) + it * 10 * MINUTE, kst(8) + it * 10 * MINUTE + 5 * MINUTE) }
        val recording = trip("now", kst(9, 30), null)

        val c = ReceiptMatcher.candidates(kst(8, 20), done + recording, emptyList())

        assertEquals(3, c.size)
        assertEquals("now", c[0].tripId)
    }

    @Test
    fun `기록 중인 운행이 시작된 뒤의 영수증은 그 운행 안이다`() {
        assertEquals(0L, ReceiptMatcher.gap(trip("now", kst(9), null), kst(11)))
    }

    @Test
    fun `라벨은 출발 시각과 도착지`() {
        assertEquals("09:12 출발 → 거래처", ReceiptMatcher.label(trip("t", kst(9, 12), kst(9, 30))))
        assertEquals(
            "09:12 출발 → 지금 기록 중",
            ReceiptMatcher.label(trip("t", kst(9, 12), null).copy(destinationLabel = null)),
        )
    }

    @Test
    fun `도착지 이름이 없으면 주소, 그것도 없으면 도착지 모름과 거리`() {
        val t = trip("t", kst(9, 12), kst(9, 30)).copy(destinationLabel = null, distanceKm = 0.7)

        assertEquals(
            "09:12 출발 → 사근동 왕십리로 222",
            ReceiptMatcher.label(t.copy(destinationAddress = "서울 성동구 사근동 왕십리로 222")),
        )
        assertEquals("09:12 출발 → 도착지 모름 · 0.7 km", ReceiptMatcher.label(t))
    }

    @Test
    fun `거래 시각은 OCR 일시, 없으면 촬영 시각`() {
        val r = receipt(datetime = "2026-11-02T09:20:00+09:00")

        assertEquals(kst(9, 20), ReceiptMatcher.receiptTime(r))
        assertEquals(kst(9, 40), ReceiptMatcher.receiptTime(r.copy(fields = r.fields.copy(datetime = "모름"))))
        assertNull(ReceiptMatcher.receiptTime(r.copy(fields = r.fields.copy(datetime = null), takenAt = null)))
    }

    @Test
    fun `검산은 금액이 수량×단가−할인과 오차 안이면 통과`() {
        val f = ReceiptFields(null, null, null, 40.0, 1700, 1000, 67_000, null)

        assertEquals(true, ReceiptArithmetic.check(f))
        assertEquals(false, ReceiptArithmetic.check(f.copy(amountWon = 68_000)))
        assertNull(ReceiptArithmetic.check(f.copy(liters = null)))
        assertFalse(ReceiptArithmetic.check(f.copy(liters = 4.0)) == true)
    }
}

internal fun receipt(datetime: String? = "2026-11-02T09:20:00+09:00") = ReceiptPayload(
    receiptId = "r-1",
    sha256 = "a".repeat(64),
    thumbUrl = "https://autolog.example/t.jpg",
    takenAt = "2026-11-02T09:40:00+09:00",
    fields = ReceiptFields("SK 판교주유소", datetime, "휘발유", 40.0, 1700, 0, 68_000, "1234"),
    confidence = mapOf("amountWon" to 0.98),
    arithOk = true,
    ocrStatus = OcrStatus.OK,
)
