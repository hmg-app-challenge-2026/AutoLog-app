package kr.autolog.vehicle.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.currentMonth
import kr.autolog.vehicle.common.tickerFlow
import kr.autolog.vehicle.data.repository.ContextRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.data.repository.TripRepository
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

class HomeViewModel(
    private val session: SessionRepository,
    context: ContextRepository,
    trips: TripRepository,
    private val navigator: Navigator,
    clock: AppClock,
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
        // 종료 조건이 되면(종료 대기) 또는 운행이 끝나면 바로 운행 결과 카드를 연다(스펙 7.3, D-28).
        // 같은 운행의 "종료 대기 → 끝남"은 한 번만 연다. [기록 이어가기]로 되돌렸다가 다시 종료 대기가 되면 다시 연다.
        viewModelScope.launch {
            session.state
                .map { it.resultTripId() }
                .distinctUntilChanged()
                // 정차·주행 중이면(정차 중 [기록 종료] 등) 다음 주차 때 연다 — 그 전에 기록이 다시 이어지면 지운다(D-31)
                .collect { id -> navigator.showWhenParked(id?.let { Route.TripResult(it) }) }
        }
    }

    fun onAction(action: HomeAction) {
        viewModelScope.launch {
            when (action) {
                HomeAction.StartTrip -> session.startTrip()
                HomeAction.EndNow -> session.requestStop()
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

/** 운행 결과 카드를 열 운행: 종료 대기 중이거나 방금 끝나 목적을 기다리는 운행. */
private fun TripSession.resultTripId(): String? = when (this) {
    is TripSession.AutoEnding -> trip.tripId
    is TripSession.PendingReview -> tripId
    else -> null
}
