package kr.autolog.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.LocalStore
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeDetail.Companion.BRANCH_MOVE
import kr.autolog.vehicle.core.PurposeDetail.Companion.CLIENT_VISIT
import kr.autolog.vehicle.core.PurposeDetail.Companion.COMMUTE_IN
import kr.autolog.vehicle.core.PurposeDetail.Companion.FIELD_WORK
import kr.autolog.vehicle.core.TripRow
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * [LocalStore] 가짜 (프론트 초안에서 옮김). [seed]면 최근 7일 운행 6건과 영수증 1건으로 시작한다:
 * 확정 3 · 목적 확인 필요 1 · 운전자 미확정 1 · 거리 미확정 1.
 * [FakeTripSession]에 넘기면 끝난 운행이 여기에 쌓인다.
 */
class FakeLocalStore(clock: Clock = Clock.systemUTC(), seed: Boolean = true) : LocalStore {
    private val rows = MutableStateFlow(if (seed) sampleRows(clock.millis()) else emptyList())
    private val inbox = MutableStateFlow(if (seed) listOf(FakeReceiptApi.SAMPLE_RECEIPT) else emptyList())

    override fun trips(month: YearMonth): Flow<List<TripRow>> = rows.map { all ->
        all.filter { YearMonth.from(Instant.ofEpochMilli(it.startTime).atZone(SEOUL)) == month }
            .sortedByDescending { it.startTime }
    }

    override val pendingReviews: Flow<List<TripRow>> =
        rows.map { all -> all.filter { it.purpose == null }.sortedByDescending { it.startTime } }

    override fun recent(limit: Int): Flow<List<TripRow>> =
        rows.map { all -> all.sortedByDescending { it.startTime }.take(limit) }

    override val receiptInbox: Flow<List<ReceiptPayload>> = inbox.asStateFlow()

    override suspend fun trip(tripId: String): TripRow? = rows.value.firstOrNull { it.tripId == tripId }

    // ---- 가짜 세션·데모가 쓰는 조작 ----

    fun upsert(row: TripRow) {
        rows.value = rows.value.filterNot { it.tripId == row.tripId } + row
    }

    /** 운행 결과 카드 [삭제하기] — 앱 A는 DISCARDED로 남기고 목록에서 뺀다. 가짜는 행을 지운다. */
    fun discard(tripId: String) {
        rows.value = rows.value.filterNot { it.tripId == tripId }
    }

    fun confirm(tripId: String, detail: PurposeDetail, note: String?, destinationLabel: String? = null) {
        rows.value = rows.value.map {
            if (it.tripId == tripId) {
                it.copy(
                    purpose = detail,
                    note = note ?: it.note,
                    destinationLabel = destinationLabel ?: it.destinationLabel,
                )
            } else {
                it
            }
        }
    }

    /** 거리 수정(F-05). 없는 운행이면 false. 실구현처럼 수정 표시를 남긴다. */
    fun editDistance(tripId: String, km: Double): Boolean {
        if (rows.value.none { it.tripId == tripId }) return false
        rows.value = rows.value.map {
            if (it.tripId == tripId) it.copy(distanceKm = km, isDistanceEdited = true) else it
        }
        return true
    }

    fun assignDriver(tripId: String, userId: String) {
        rows.value = rows.value.map { if (it.tripId == tripId) it.copy(driverUserId = userId) else it }
    }

    fun pushReceipt(payload: ReceiptPayload) {
        inbox.value = inbox.value.filterNot { it.receiptId == payload.receiptId } + payload
    }

    fun ackReceipt(receiptId: String) {
        inbox.value = inbox.value.filterNot { it.receiptId == receiptId }
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        /** 샘플 장소 이름 → 주소(`광역 기초 읍면동 도로명 번호`). 화면의 이름 + 주소 2줄을 보여 주려고 둔다. */
        val SAMPLE_ADDRESSES: Map<String, String> =
            mapOf(
                "본사" to FakeTripSession.SAMPLE_ORIGIN_ADDRESS,
                "판교 테크노밸리 거래처" to FakeTripSession.SAMPLE_DESTINATION_ADDRESS,
                "판교 거래처" to FakeTripSession.SAMPLE_DESTINATION_ADDRESS,
                "수원 지점" to "경기 수원시 영통구 매탄동 삼성로 129",
                "자택" to "부산 기장군 장안읍 해맞이로 10-1",
                "강남 고객사" to "서울 강남구 역삼동 테헤란로 152",
            )
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR

        private fun sampleRows(now: Long): List<TripRow> {
            fun row(
                id: String,
                ago: Long,
                minutes: Long,
                driver: String?,
                from: String,
                to: String,
                km: Double?,
                purpose: PurposeDetail?,
            ) = TripRow(
                id,
                now - ago,
                now - ago + minutes * 60_000,
                driver,
                from,
                to,
                km,
                purpose,
                originAddress = SAMPLE_ADDRESSES[from],
                destinationAddress = SAMPLE_ADDRESSES[to],
            )
            return listOf(
                row("t-s1", 2 * HOUR, 35, "u-1", "본사", "판교 테크노밸리 거래처", 23.4, null),
                row("t-s2", DAY, 50, "u-2", "판교 거래처", "수원 지점", 41.2, BRANCH_MOVE),
                row("t-s3", 2 * DAY + 3 * HOUR, 25, "u-1", "자택", "본사", 12.8, COMMUTE_IN),
                row("t-s4", 3 * DAY, 20, null, "본사", "강남 고객사", 8.6, CLIENT_VISIT),
                row("t-s5", 5 * DAY, 40, "u-3", "본사", "본사", null, FIELD_WORK),
                row("t-s6", 7 * DAY, 55, "u-2", "수원 지점", "본사", 40.9, BRANCH_MOVE),
            )
        }
    }
}
