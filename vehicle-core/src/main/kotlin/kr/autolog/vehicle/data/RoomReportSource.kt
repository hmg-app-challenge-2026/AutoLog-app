package kr.autolog.vehicle.data

import kotlinx.coroutines.flow.first
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.report.ReportExpense
import kr.autolog.vehicle.report.ReportSchedule
import kr.autolog.vehicle.report.ReportSource
import kr.autolog.vehicle.report.ReportTrip
import kr.autolog.vehicle.report.ReviewFlag

/** 운행기록부 재료를 Room에서 읽는다. 기록 중인 운행과 버린 운행은 빠진다. */
class RoomReportSource(
    private val trips: TripDao,
    private val refs: ReferenceDao,
    private val expenses: ExpenseDao,
    private val vehicleId: () -> String?,
) : ReportSource {
    override suspend fun trips(from: Long, to: Long): List<ReportTrip> {
        val users = refs.observeUsers().first().associateBy { it.userId }
        return trips.tripsBetween(from, to).filter { it.status != TripStatus.RECORDING }.map { t ->
            val u = t.userId?.let { users[it] }
            ReportTrip(
                tripId = t.tripId,
                startTime = t.startTime,
                endTime = t.endTime,
                userId = t.userId,
                userName = u?.name ?: t.userId,
                dept = u?.dept,
                originLabel = t.originLabel,
                destinationLabel = t.destinationLabel,
                startKm = t.startOdometerKm,
                endKm = t.endOdometerKm,
                distanceKm = t.reportedKm(),
                purpose = t.confirmedPurpose(),
                note = t.purposeNote,
                timeEstimated = t.timeEstimated,
                stopLabels = trips.stops(t.tripId).mapNotNull { it.label },
                originAddress = t.originAddress,
                destinationAddress = t.destinationAddress,
                isDistanceEdited = t.editedDistanceKm != null,
                odometerDistanceKm = t.distanceKm,
            )
        }
    }

    override suspend fun expenses(from: Long, to: Long): List<ReportExpense> {
        val list = expenses.expensesBetween(from, to)
        val links = expenses.linksForExpenses(list.map { it.expenseId }).groupBy({ it.expenseId }, { it.tripId })
        return list.map { e ->
            ReportExpense(
                expenseId = e.expenseId,
                occurredAt = e.occurredAt,
                amountWon = e.amountWon,
                liters = e.liters,
                unitPrice = e.unitPrice,
                discountWon = e.discountWon,
                fuelType = e.fuelType,
                merchant = e.merchant,
                cardLast4 = e.cardLast4,
                source = e.source,
                receiptSha256 = e.receiptSha256,
                tripIds = links[e.expenseId].orEmpty(),
            )
        }
    }

    override suspend fun schedules(from: Long, to: Long): List<ReportSchedule> {
        val car = vehicleId() ?: return emptyList()
        val users = refs.observeUsers().first().associateBy { it.userId }
        return refs.schedules(car, from, to).map {
            ReportSchedule(it.scheduleId, it.userId?.let { id -> users[id]?.name }, it.startsAt, it.endsAt)
        }
    }

    override suspend fun gasStations(): Set<String> = refs.places().filter {
        it.kind == PlaceKind.GAS_STATION
    }.map { it.label }.toSet()

    override suspend fun saveReviewFlags(flags: Map<String, Set<ReviewFlag>>) {
        // 빈 집합 = 해소됨 → null로 지운다
        flags.forEach { (id, f) ->
            trips.setReviewFlags(id, f.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name })
        }
    }
}
