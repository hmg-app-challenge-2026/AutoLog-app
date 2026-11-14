package kr.autolog.vehicle.data.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.AppZone
import kr.autolog.vehicle.core.LocalStore
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeDetail.Companion.BRANCH_MOVE
import kr.autolog.vehicle.core.PurposeDetail.Companion.CLIENT_VISIT
import kr.autolog.vehicle.core.PurposeDetail.Companion.COMMUTE_IN
import kr.autolog.vehicle.core.PurposeDetail.Companion.FIELD_WORK
import kr.autolog.vehicle.core.TripRow
import kr.autolog.vehicle.core.UploadState
import kr.autolog.vehicle.core.UploadState.DONE
import kr.autolog.vehicle.core.UploadState.FAILED
import kr.autolog.vehicle.core.UploadState.PENDING
import kr.autolog.vehicle.fake.FakeReceiptApi
import java.time.Instant
import java.time.YearMonth
import kr.autolog.vehicle.fake.FakeLocalStore as CoreFakeStore

/** 화면·엑셀의 날짜 기준(Backend CONVENTIONS 6.3). */

fun YearMonth.containsEpoch(epochMs: Long): Boolean =
    YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(AppZone)) == this

/**
 * 앱 A [LocalStore](Room)가 올 때까지 쓰는 가짜. [seed]면 최근 7일 운행 6건과 영수증 1건으로 시작한다:
 * 확정 3 · 목적 확인 필요 1 · 운전자 미확정 1 · 거리 미확정 1.
 */
class FakeLocalStore(clock: AppClock, seed: Boolean = true) : LocalStore {
    private val rows = MutableStateFlow(if (seed) sampleRows(clock.nowMs()) else emptyList())
    private val inbox = MutableStateFlow(if (seed) listOf(FakeReceiptApi.SAMPLE_RECEIPT) else emptyList())

    override fun trips(month: YearMonth): Flow<List<TripRow>> =
        rows.map { all -> all.filter { month.containsEpoch(it.startTime) }.sortedByDescending { it.startTime } }

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

    /** 목적 확정. [destinationLabel]은 목적지 없이 끝난 운행에서 고른 도착지(실구현 `TripSessionManager.confirmPurpose`와 같은 규칙). */
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
        rows.value =
            rows.value.map { if (it.tripId == tripId) it.copy(distanceKm = km, isDistanceEdited = true) else it }
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

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        fun sampleRows(now: Long): List<TripRow> {
            fun row(
                id: String,
                ago: Long,
                minutes: Long,
                driver: String?,
                from: String,
                to: String,
                km: Double?,
                p: PurposeDetail?,
                up: UploadState,
            ) = TripRow(
                id,
                now - ago,
                now - ago + minutes * 60_000,
                driver,
                from,
                to,
                km,
                p,
                upload = up,
                originAddress = CoreFakeStore.SAMPLE_ADDRESSES[from],
                destinationAddress = CoreFakeStore.SAMPLE_ADDRESSES[to],
            )
            return listOf(
                row("t-s1", 2 * HOUR, 35, "u-1", "본사", "판교 테크노밸리 거래처", 23.4, null, PENDING),
                row("t-s2", DAY, 50, "u-2", "판교 거래처", "수원 지점", 41.2, BRANCH_MOVE, DONE),
                row("t-s3", 2 * DAY + 3 * HOUR, 25, "u-1", "자택", "본사", 12.8, COMMUTE_IN, DONE),
                row("t-s4", 3 * DAY, 20, null, "본사", "강남 고객사", 8.6, CLIENT_VISIT, DONE),
                row("t-s5", 5 * DAY, 40, "u-3", "본사", "본사", null, FIELD_WORK, FAILED),
                row("t-s6", 7 * DAY, 55, "u-2", "수원 지점", "본사", 40.9, BRANCH_MOVE, DONE),
            )
        }
    }
}
