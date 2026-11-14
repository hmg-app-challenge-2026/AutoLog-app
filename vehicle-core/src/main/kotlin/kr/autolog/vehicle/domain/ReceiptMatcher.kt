package kr.autolog.vehicle.domain

import kr.autolog.shared.PlaceKind
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.TripCandidate
import kr.autolog.vehicle.core.TripSummary
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 영수증을 붙일 운행 후보 (F-19). 거래 시각 ±2시간 안의 운행, 주유소에서 정차한 운행 먼저, 시각이 가까운 순, 최대 3개.
 * 순수 함수라 JVM 테스트가 된다.
 */
object ReceiptMatcher {
    const val WINDOW_MS = 2 * 60 * 60 * 1000L
    const val MAX_CANDIDATES = 3
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 영수증 거래 시각. OCR 일시가 없거나 못 읽으면 촬영 시각. 둘 다 없으면 null. */
    fun receiptTime(r: ReceiptPayload): Long? = parse(r.fields.datetime) ?: parse(r.takenAt)

    fun candidates(at: Long, trips: List<TripSummary>, places: List<Place>): List<TripCandidate> {
        val stations = places.filter { it.kind == PlaceKind.GAS_STATION }
        return trips
            .map { it to gap(it, at) }
            .filter { (_, g) -> g <= WINDOW_MS }
            .map { (t, g) -> Triple(t, g, hasFuelStop(t, stations)) }
            // 지금 기록 중인 운행(endTime 없음)은 늘 뽑는다 — 운행 중 주유하고 바로 영수증을 찍는 게 가장 흔하다
            .sortedWith(
                compareByDescending<Triple<TripSummary, Long, Boolean>> { it.first.endTime == null }
                    .thenByDescending { it.third }
                    .thenBy { it.second },
            )
            .take(MAX_CANDIDATES)
            // 고르는 건 가까운 순, 보여 주는 건 최근 출발이 위(2026-11-15 사용자 결정)
            .sortedByDescending { (t, _, _) -> t.startTime }
            .map { (t, _, fuel) -> TripCandidate(t.tripId, t.startTime, t.endTime, label(t), fuel) }
    }

    /** 운행 시간 안이면 0, 밖이면 가까운 끝까지의 거리(ms). */
    internal fun gap(t: TripSummary, at: Long): Long {
        val end = t.endTime ?: Long.MAX_VALUE // 기록 중이면 아직 끝나지 않았다
        return when {
            at in t.startTime..end -> 0
            at < t.startTime -> t.startTime - at
            else -> at - end
        }
    }

    internal fun hasFuelStop(t: TripSummary, stations: List<Place>): Boolean {
        val labels = t.stopLabels + listOfNotNull(t.destinationLabel)
        if (labels.any { it.contains("주유") }) return true
        val lat = t.endLat ?: return false
        val lng = t.endLng ?: return false
        return PlaceMatcher.match(lat, lng, stations) != null
    }

    /**
     * 운행 한 줄 표시. 예: "09:12 본사 → 판교 거래처", 장소를 모르면 "09:12 출발 · 23.4 km".
     * [withDistance]면 장소가 있어도 거리를 붙인다(F-20 "이전 운행(09:00, 23.4 km)").
     */
    /**
     * 운행 한 줄: `09:12 출발 → 거래처`. 어디로 갔는지가 운행을 구분하는 단서라 도착지를 늘 보인다(2026-11-15 사용자 결정).
     * 도착지 이름이 없으면 주소(광역·기초 뺀 `동 도로명 번호`), 그것도 없으면 "도착지 모름"과 거리.
     */
    fun label(t: TripSummary, withDistance: Boolean = false): String {
        val time = HHMM.format(Instant.ofEpochMilli(t.startTime).atZone(KST))
        val km = t.distanceKm?.let { " · ${String.format(Locale.ROOT, "%.1f", it)} km" } ?: ""
        val dest = t.destinationLabel?.takeIf { it.isNotBlank() } ?: shortAddress(t.destinationAddress)
        if (t.endTime == null) return "$time 출발 → ${dest ?: RECORDING_DEST}" // 기록 중 — 거리는 아직 없다
        return "$time 출발 → ${dest ?: UNKNOWN_DEST}" + if (withDistance || dest == null) km else ""
    }

    private const val UNKNOWN_DEST = "도착지 모름"
    private const val RECORDING_DEST = "지금 기록 중"

    /** `서울 성동구 사근동 왕십리로 222` → `사근동 왕십리로 222`. 짧으면 그대로. */
    internal fun shortAddress(address: String?): String? {
        val parts = address?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return null
        if (parts.isEmpty()) return null
        return (if (parts.size > 3) parts.drop(2) else parts).joinToString(" ")
    }

    private fun parse(iso: String?): Long? =
        iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
}

/** 영수증 금액 검산 (F-19 "검산 실패 시 확정 비활성"). 서버 ReceiptVerifier와 같은 기준. */
object ReceiptArithmetic {
    const val MIN_TOLERANCE_WON = 100
    const val TOLERANCE_RATIO = 0.005

    /** |금액 − (L × 단가 − 할인)| ≤ max(100원, 0.5%). 수량·단가가 없으면 금액만 있는 영수증이라 통과(null). */
    fun check(f: ReceiptFields): Boolean? {
        val amount = f.amountWon ?: return null
        val liters = f.liters ?: return null
        val unit = f.unitPrice ?: return null
        val expected = (liters * unit).roundToLong() - (f.discountWon ?: 0)
        val tolerance = max(MIN_TOLERANCE_WON.toDouble(), amount * TOLERANCE_RATIO)
        return abs(amount - expected) <= tolerance
    }
}
