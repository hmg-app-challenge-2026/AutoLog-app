package kr.autolog.vehicle.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.currentMonth
import kr.autolog.vehicle.common.tickerFlow
import kr.autolog.vehicle.data.repository.ContextRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.data.repository.TripRepository
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.data.usage.UsageTier
import kr.autolog.vehicle.data.usage.VehicleUsageRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

class HomeViewModel(
    private val session: SessionRepository,
    context: ContextRepository,
    trips: TripRepository,
    private val navigator: Navigator,
    clock: AppClock,
    /** 지금 주차인가 — [기록 종료]를 주차 중엔 5초 대기로, 정차·주행 중엔 바로 끝내기로(D-34). 종료 대기 카드도 주차 중에만(D-35). */
    private val usage: VehicleUsageRepository,
    ticker: Flow<Long> = tickerFlow(clock),
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(session.state, session.missedKm, context.destinationLabel, ::Triple),
            // 대기 왼쪽 = 이번 달 요약, 오른쪽 = 최근 운행 기록(D-25). 화면이 열려 있는 동안 달이 바뀌는 경우는 다음 진입 때 맞춘다
            combine(trips.summary(clock.currentMonth()), trips.recent(RECENT_TRIPS), ::Pair),
            ticker,
        ) { (s, missed, dest), (month, recent), now ->
            reduceHome(s, missed, dest, now, month, recent)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // 운행이 끝나면 단계와 무관하게 바로 운행 결과 카드를 연다(스펙 7.3, D-28·D-35) — 정차·주행 중엔 버튼 없이 5초 뒤 저절로 저장.
        // 종료 대기(되돌릴 수 있는 카운트다운)는 주차 중에만 카드로 — 정차 중엔 Home 상태 줄이 남은 시간을 말한다(D-31).
        // 같은 운행의 "종료 대기 → 끝남"은 한 번만 연다(같은 Route). [기록 이어가기]로 되돌렸다가 다시 종료 대기가 되면 다시 연다
        viewModelScope.launch {
            combine(session.state, usage.tier) { s, tier -> s.resultRoute(parked = tier == UsageTier.FULL) }
                .distinctUntilChanged()
                .collect { route -> route?.let(navigator::showTripResult) }
        }
    }

    fun onAction(action: HomeAction) {
        viewModelScope.launch {
            when (action) {
                HomeAction.StartTrip -> session.startTrip()
                // 주차 중: 자동 종료와 같은 5초 대기(결과 카드에서 되돌릴 수 있다).
                // 정차·주행 중: 바로 끝낸다 — 되돌릴 버튼이 없으니 기다릴 이유가 없다. 결과 카드는 버튼 없이 열려 5초 뒤 저장되고(D-35),
                // 달리는 중이었으면 앱 A가 다음 정차까지 새 운행을 다시 시작하지 않는다(D-34)
                HomeAction.EndNow ->
                    if (usage.tier.value == UsageTier.FULL) session.requestStop() else session.endNow()
                HomeAction.AcceptMissedTrip -> session.acceptMissedTrip()
                HomeAction.IgnoreMissedTrip -> session.ignoreMissedTrip()
                is HomeAction.Recover -> session.resolveRecovery(action.action)
                HomeAction.AddFuelRecord -> navigator.push(Route.ReceiptQr)
                HomeAction.OpenRecords -> navigator.push(Route.History)
                HomeAction.OpenSettings -> navigator.push(Route.Settings)
            }
        }
    }
}

/** 운행 결과 카드: 방금 끝나 목적을 기다리는 운행(단계 무관), 또는 종료 대기 중인 운행(주차 중에만). */
private fun TripSession.resultRoute(parked: Boolean): Route.TripResult? = when (this) {
    is TripSession.AutoEnding -> if (parked) Route.TripResult(trip.tripId) else null
    is TripSession.PendingReview -> Route.TripResult(tripId)
    else -> null
}
