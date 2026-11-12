package kr.autolog.vehicle.ui.history

import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.HistoryItem
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.ui.design.formatKm
import kr.autolog.vehicle.ui.trips.toTripRowUi
import java.time.YearMonth

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
): HistoryUiState = HistoryUiState(
    month = month,
    loading = items == null,
    canGoNext = month < current,
    tripCount = summary?.tripCount ?: 0,
    totalKm = formatKm(summary?.totalKm ?: 0.0).orEmpty(),
    rows = items.orEmpty().map { it.toTripRowUi(now) },
    driverChoices = drivers.take(DRIVER_CHOICES).map { DriverChoice(it.userId, it.name) },
)
