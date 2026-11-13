package kr.autolog.vehicle.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.LocalStore
import kr.autolog.vehicle.core.TripRow
import java.time.YearMonth
import java.time.ZoneId

/** 화면이 읽는 운행 기록 — [LocalStore]의 Room 구현 (F-12, F-19). */
class RoomLocalStore(private val trips: TripDao, private val expenses: ExpenseDao, private val zone: ZoneId = SEOUL) :
    LocalStore {
    override fun trips(month: YearMonth): Flow<List<TripRow>> {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return trips.observeFinishedBetween(from, to).map { list -> list.map { it.toRow() } }
    }

    override val pendingReviews: Flow<List<TripRow>> = trips.observePendingReviews().map { list ->
        list.map { it.toRow() }
    }

    override fun recent(limit: Int): Flow<List<TripRow>> = trips.observeRecent(limit).map { list ->
        list.map { it.toRow() }
    }

    override val receiptInbox: Flow<List<ReceiptPayload>> = expenses.observePendingReceipts().map { list ->
        list.mapNotNull {
            runCatching { AutoLogJson.decodeFromString(ReceiptPayload.serializer(), it.payloadJson) }.getOrNull()
        }
    }

    override suspend fun trip(tripId: String): TripRow? = trips.trip(tripId)?.toRow()

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

/** Room 운행 → 화면 한 줄. 비어 있는 값은 미확정이다(추정하지 않는다). */
internal fun TripEntity.toRow() = TripRow(
    tripId = tripId,
    startTime = startTime,
    endTime = endTime,
    driverUserId = userId,
    originLabel = originLabel,
    destinationLabel = destinationLabel,
    distanceKm = reportedKm(),
    purpose = confirmedPurpose(),
    note = purposeNote,
    originAddress = originAddress,
    destinationAddress = destinationAddress,
    isDistanceEdited = editedDistanceKm != null,
)
