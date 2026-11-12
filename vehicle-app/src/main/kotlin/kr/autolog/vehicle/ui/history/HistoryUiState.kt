package kr.autolog.vehicle.ui.history

import kr.autolog.vehicle.ui.trips.TripRowUi
import java.time.YearMonth

/** 운전자 미확정 줄에 보일 후보 수(스펙 7.8, F-20 — 최근 운전자 최대 3명). */
const val DRIVER_CHOICES = 3

/**
 * 운행 기록 조회(스펙 7.8, F-12). 한 달씩 본다 — [month]가 지금 달이면 다음 달로 넘어갈 수 없다(미래 없음).
 * 상단 합계는 저장소가 계산한 값 그대로(엑셀 요약과 같은 계산 — 화면은 표시만).
 */
data class HistoryUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    val canGoNext: Boolean = false,
    val tripCount: Int = 0,
    /** 거리 미확정 운행은 빠진 합계(스펙 7.7). */
    val totalKm: String = "0.0",
    /** 최신 순. */
    val rows: List<TripRowUi> = emptyList(),
    /** 운전자 미확정 줄에서 1탭으로 고를 후보(추천 순 최대 [DRIVER_CHOICES]명). */
    val driverChoices: List<DriverChoice> = emptyList(),
)

data class DriverChoice(val userId: String, val name: String)

sealed interface HistoryAction {
    data object PrevMonth : HistoryAction

    data object NextMonth : HistoryAction

    /** 줄 탭 → 운행 결과 카드(사후 확인, 카운트다운 없음 — D-29). */
    data class Open(val tripId: String) : HistoryAction

    /** 운전자 미확정 줄에서 후보 1탭(F-20). */
    data class AssignDriver(val tripId: String, val userId: String) : HistoryAction

    /** 맨 위 [운행기록부 내보내기] → 운행기록부(스펙 7.7). */
    data object OpenReport : HistoryAction

    data object Back : HistoryAction
}
