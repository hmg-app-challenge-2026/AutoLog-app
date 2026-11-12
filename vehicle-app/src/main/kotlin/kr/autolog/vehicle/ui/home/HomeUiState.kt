package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import kr.autolog.vehicle.data.repository.RecoveryAction
import kr.autolog.vehicle.ui.trips.TripRowUi

/** 스펙 7.2 상태 3가지(종료 대기는 Home이 아니라 운행 결과 카드가 맡는다 — D-28). 상태 계산은 [reduceHome](HomeReducer.kt). */
enum class HomePhase { READY, RECORDING_PARKED, NEEDS_RECOVERY }

/** 대기 상태의 주인공 — 이번 달 요약(스펙 7.2, D-23). */
data class MonthUi(val tripCount: Int, val totalKm: String, val businessKm: String)

/** Home 가운데 섹션 한 칸: 소제목 + 내용. **무엇을** 보여 줄지는 [homeSections], 창 크기별 **배치**는 [HomeScreen]이 정한다. */
internal class HomeSection(val title: String, val content: @Composable ColumnScope.() -> Unit)

data class HomeUiState(
    val phase: HomePhase = HomePhase.READY,
    val month: MonthUi? = null,
    /** 기록 중 거리. null = 계기판 미확정(추정 금지). */
    val tripKm: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val elapsedMin: Int? = null,
    /**
     * 종료 대기 중이고 끝나기 [AUTO_END_NOTICE_SEC]초 안이면 남은 초(움직임 없음 180초 · 내비 도착 · 기어 P · [기록 종료] 5초).
     * 주차 중엔 운행 결과 카드가 덮어 보이지 않고, 정차 중(카드를 열지 않음)에 상태 줄이 "58초 후 기록 종료"로 알린다 —
     * 그 전(신호 대기 등)엔 "35분 경과" 그대로(D-31·D-37).
     */
    val autoEndInSec: Int? = null,
    /** 대기 오른쪽 "최근 운행 기록", 최신 순 최대 [RECENT_TRIPS]건(D-25). 목적이 없으면 "목적 미입력" — 고치는 곳은 운행 기록 조회(D-26). */
    val recentTrips: List<TripRowUi> = emptyList(),
    val recoveryExtraKm: String? = null,
    /** 앱이 꺼진 동안 달린 거리(F-10). 대기 상태에서만 채워지고, 있으면 Home이 [추가하기]·X 팝업으로 한 번 묻는다(D-26). */
    val missedKm: String? = null,
)

sealed interface HomeAction {
    /** 수동 시작 — 보통은 출발하면 자동으로 시작된다(타일 첫 칸 [기록 시작]). */
    data object StartTrip : HomeAction

    data object EndNow : HomeAction

    data object AcceptMissedTrip : HomeAction

    data object IgnoreMissedTrip : HomeAction

    data class Recover(val action: RecoveryAction) : HomeAction

    /** 주유 기록 추가 — 영수증 QR로 들어간다(스펙 7.4). */
    data object AddFuelRecord : HomeAction

    /** 운행 기록 조회(이력 + 운행기록부 만들기·보내기, D-23). 최근 운행 줄을 눌러도 여기로 — 목적 수정도 여기서(D-26). */
    data object OpenRecords : HomeAction

    data object OpenSettings : HomeAction
}

/** "최근 운행 기록"에 보이는 최대 건수(D-25). */
const val RECENT_TRIPS = 3
