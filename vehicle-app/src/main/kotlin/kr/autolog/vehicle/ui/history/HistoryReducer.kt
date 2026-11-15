package kr.autolog.vehicle.ui.history

import kr.autolog.vehicle.common.AppZone
import kr.autolog.vehicle.core.FuelRecord
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.HistoryItem
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.ui.trips.toTripRowUi
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 운행 기록 조회 상태 계산(순수 함수, HistoryReducerTest 대상). 입력은 Repository 값 타입만.
 * [items]가 null이면 아직 불러오는 중. [current]는 지금 달 — 그보다 뒤로는 넘어갈 수 없다.
 */
internal fun reduceHistory(
    month: YearMonth,
    current: YearMonth,
    items: List<HistoryItem>?,
    summary: MonthSummary?,
    drivers: List<DriverProfile>,
    now: Long,
    fuels: List<FuelRecord> = emptyList(),
): HistoryUiState = HistoryUiState(
    month = month,
    loading = items == null,
    canGoNext = month < current,
    tripCount = summary?.tripCount ?: 0,
    fuelCount = fuels.size,
    records = (
        items.orEmpty().map { HistoryRecordUi.Trip(it.toTripRowUi(now), it.startTime) } +
            fuels.map { fuel ->
                val at = Instant.ofEpochMilli(fuel.occurredAt).atZone(AppZone)
                HistoryRecordUi.Fuel(
                    id = "fuel:${fuel.expenseId}",
                    occurredAt = fuel.occurredAt,
                    dayOfMonth = at.dayOfMonth,
                    time = at.format(FUEL_TIME),
                    amountWon = fuel.amountWon,
                    liters = fuel.liters,
                    merchant = fuel.merchant,
                )
            }
        ).sortedByDescending { it.occurredAt },
    driverChoices = drivers.take(DRIVER_CHOICES).map { DriverChoice(it.userId, it.name) },
)

private val FUEL_TIME = DateTimeFormatter.ofPattern("HH:mm")
