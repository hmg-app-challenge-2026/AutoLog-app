package kr.autolog.vehicle.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.currentMonth
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.data.repository.TripRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.nav.TripResultMode

/** 운행 기록 조회(스펙 7.8, F-12·F-20). 상태 계산은 [reduceHistory], 여기는 달 선택·Repository 호출·화면 이동만. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val trips: TripRepository,
    private val profile: ProfileRepository,
    private val session: SessionRepository,
    private val navigator: Navigator,
    private val clock: AppClock,
) : ViewModel() {
    private val month = MutableStateFlow(clock.currentMonth())

    val uiState: StateFlow<HistoryUiState> =
        month.flatMapLatest { m ->
            combine(trips.history(m), trips.summary(m), profile.orderedProfiles()) { items, summary, drivers ->
                reduceHistory(m, clock.currentMonth(), items, summary, drivers, clock.nowMs())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(month.value))

    fun onAction(action: HistoryAction) {
        when (action) {
            HistoryAction.PrevMonth -> month.update { it.minusMonths(1) }
            // 지금 달보다 뒤(미래)로는 넘어가지 않는다
            HistoryAction.NextMonth -> month.update { if (it < clock.currentMonth()) it.plusMonths(1) else it }
            is HistoryAction.Open -> navigator.push(Route.TripResult(action.tripId, TripResultMode.REVIEW))
            is HistoryAction.AssignDriver -> viewModelScope.launch {
                session.assignDriver(action.tripId, action.userId)
            }
            HistoryAction.OpenReport -> navigator.push(Route.Report)
            HistoryAction.Back -> navigator.pop()
        }
    }
}
