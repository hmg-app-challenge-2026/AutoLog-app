package kr.autolog.vehicle.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.ExpenseDraft
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.RecentUse
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.ScheduleInfo
import kr.autolog.vehicle.core.TripSummary
import kr.autolog.vehicle.core.UserInfo
import java.time.Clock
import java.util.UUID

/**
 * 앱 B 로직이 읽고 쓰는 차 안 데이터 — [DomainStore]의 Room 구현. 동작 기준은 `fake/InMemoryDomainStore`.
 * 영수증 ack는 [ackRemote](서버 `POST /v1/inbox/{id}/ack`)로 보낸다. 실패하면 행을 남겨 InboxSyncWorker가 다시 보낸다.
 */
class RoomDomainStore(
    private val trips: TripDao,
    private val refs: ReferenceDao,
    private val expenses: ExpenseDao,
    private val clock: Clock = Clock.systemUTC(),
    private val ackRemote: suspend (inboxId: Long) -> Boolean = { false },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : DomainStore {
    override fun users(): Flow<List<UserInfo>> = refs.observeUsers().map { list -> list.map { it.toInfo() } }

    override suspend fun recentUsers(limit: Int): List<RecentUse> =
        refs.recentUsers(limit).map { RecentUse(it.userId, it.lastUsedAt) }

    override suspend fun places(): List<PlaceInfo> = refs.places().map { it.toInfo() }

    /** 운전자가 정해지지 않은 배차는 뺀다(ScheduleInfo.userId가 필수). */
    override suspend fun schedules(vehicleId: String, from: Long, to: Long): List<ScheduleInfo> =
        refs.schedules(vehicleId, from, to).mapNotNull { s ->
            s.userId?.let { ScheduleInfo(s.scheduleId, it, s.startsAt, s.endsAt, s.destPlaceId, s.purposeDetail) }
        }

    override suspend fun trips(from: Long, to: Long): List<TripSummary> = trips.tripsBetween(from, to + 1).map { t ->
        TripSummary(
            tripId = t.tripId,
            vehicleId = t.vehicleId,
            userId = t.userId,
            startTime = t.startTime,
            endTime = t.endTime,
            distanceKm = t.reportedKm(),
            originLabel = t.originLabel,
            destinationLabel = t.destinationLabel,
            endLat = t.destinationLat,
            endLng = t.destinationLng,
            originAddress = t.originAddress,
            destinationAddress = t.destinationAddress,
            confirmedPurpose = t.confirmedPurpose(),
            stopLabels = trips.stops(t.tripId).mapNotNull { it.label },
        )
    }

    override fun recipients(): Flow<List<RecipientInfo>> =
        refs.observeRecipients().map { list -> list.map { RecipientInfo(it.email, it.name, it.origin) } }

    override suspend fun saveRecipient(r: RecipientInfo) =
        refs.upsertRecipient(RecipientEntity(r.email, r.name, Origin.LOCAL))

    override suspend fun deleteLocalRecipient(email: String) = refs.deleteLocalRecipient(email)

    override suspend fun saveUser(u: UserInfo) =
        refs.upsertUser(UserEntity(u.userId, u.name, u.dept, u.email, Origin.LOCAL))

    override suspend fun savePlace(p: PlaceInfo) = refs.upsertPlace(
        PlaceEntity(p.placeId, p.label, p.kind, p.lat, p.lng, p.radiusM, p.defaultPurpose, Origin.LOCAL),
    )

    override suspend fun hasReceipt(sha256: String): Boolean = expenses.hasReceipt(sha256)

    override suspend fun saveExpense(e: ExpenseDraft): String {
        val id = newId()
        expenses.saveExpense(
            ExpenseEntity(
                expenseId = id,
                occurredAt = e.occurredAt,
                amountWon = e.amountWon,
                liters = e.liters,
                unitPrice = e.unitPrice,
                discountWon = e.discountWon,
                fuelType = e.fuelType,
                merchant = e.merchant,
                cardLast4 = e.cardLast4?.filter { it.isDigit() }?.takeLast(4),
                source = e.source,
                receiptId = e.receiptId,
                receiptSha256 = e.receiptSha256,
                thumbUrl = e.thumbUrl,
                ocrConfidenceJson = e.ocrConfidence.takeIf { it.isNotEmpty() }
                    ?.let { m -> JsonObject(m.mapValues { JsonPrimitive(it.value) }).toString() },
                editedFields = e.editedFields.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
                createdAt = clock.millis(),
            ),
            e.tripId,
        )
        return id
    }

    override suspend fun ackReceipt(receiptId: String) {
        expenses.markReceiptConfirmed(receiptId, clock.millis())
        val inboxId = expenses.receipt(receiptId)?.inboxId ?: return
        if (runCatching { ackRemote(inboxId) }.getOrDefault(false)) expenses.deleteReceipt(receiptId)
    }
}

internal fun UserEntity.toInfo() = UserInfo(userId, name, dept, email, origin)

internal fun PlaceEntity.toInfo() = PlaceInfo(placeId, label, kind, lat, lng, radiusM, defaultPurpose, origin)

/** 사람이 확정한 목적만. 추천은 확정이 아니다. */
internal fun TripEntity.confirmedPurpose(): PurposeDetail? =
    if (purposeSource == PurposeSource.USER_CONFIRMED) PurposeDetail.fromName(purposeDetail) else null

/** 보여 줄 거리: 사용자가 고친 값이 있으면 그것, 없으면 계기판 차이. */
internal fun TripEntity.reportedKm(): Double? = editedDistanceKm ?: distanceKm
