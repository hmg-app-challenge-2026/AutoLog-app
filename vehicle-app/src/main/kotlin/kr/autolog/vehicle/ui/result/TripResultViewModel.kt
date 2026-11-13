package kr.autolog.vehicle.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.tickerFlow
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.data.repository.TripResult
import kr.autolog.vehicle.data.repository.TripResultRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.nav.TripResultMode

/**
 * 운행 결과 카드(스펙 7.3, D-27·D-28). 두 단계를 한 카드가 이어서 맡는다:
 * 1. **종료 대기**(종료 조건은 됐지만 앱 A가 아직 끝내기 전): 남은 시간 = 앱 A 종료 시각. [기록 이어가기]로 되돌릴 수 있다.
 *    시간이 다 되면(또는 [메인 화면으로]) 운행이 끝나고 → 지금 선택으로 저장 → 들어온 화면으로.
 * 2. **끝난 운행**(직접 [기록 종료]·시동 끔·알림·이력에서 연 경우): 자체 5초 카운트다운 → 저장 → 돌아간다.
 * 저장을 마치면 곧바로 돌아가지 않고 **저장 완료 화면**(`saved`)을 보인다 — 체크를 다 그린 뒤([TripResultAction.CheckDrawn])
 * [SAVED_HOLD_MS]가 지나거나 [확인]을 누르면 들어온 화면으로. [기록 이어가기]·주행 시작으로 떠날 때는 보이지 않는다.
 * [수정하기] 중엔 돌아가지 않는다(운행 자체는 앱 A 시각에 끝나고, 고른 목적은 그때·[저장하기] 때 저장). [저장하기]는 보기·수정 어느 쪽이든
 * 지금 선택으로 저장하고 돌아간다 — 시간이 다 됐을 때 저절로 일어나는 일과 같다.
 * 저장하지 않고 화면이 닫히면(주행 시작 등) 그 운행은 "목적 미입력"으로 남는다.
 *
 * [parked] = 지금 주차인가(D-35). 정차·주행 중엔 화면에 버튼이 없다 — [수정하기] 중에 출발하면 수정을 접고 지금 선택으로
 * 다시 5초 자동 저장으로 돌아간다(누를 수 없는 화면에 멈춰 있지 않게).
 *
 * [mode] = REVIEW(운행 기록 조회에서 연 사후 확인, D-29): 카운트다운도 자동 저장도 없다. 목적 미입력 운행은 수정 모드로 열리고,
 * [저장]을 눌러야만 저장한 뒤 목록으로 돌아간다. 왼쪽 위 <(Close)는 저장하지 않고 돌아간다. [삭제하기]는 보기·수정 모두(D-39).
 */
class TripResultViewModel(
    private val tripId: String,
    private val results: TripResultRepository,
    private val session: SessionRepository,
    private val navigator: Navigator,
    private val clock: AppClock,
    private val mode: TripResultMode = TripResultMode.AFTER_TRIP,
    parked: Flow<Boolean> = flowOf(true),
    ticker: Flow<Long> = tickerFlow(clock),
) : ViewModel() {
    private val review = mode == TripResultMode.REVIEW
    private val interaction = MutableStateFlow(TripResultInteraction())
    private val loaded = MutableStateFlow<Loaded>(Loaded.NotYet)
    private var current: TripResult? = null
    private var countdown: Job? = null
    private var hold: Job? = null
    private var finishing = false
    private var deleting = false
    private var left = false
    private var parkedNow = true

    val uiState: StateFlow<TripResultUiState> =
        // ticker는 1초마다 다시 계산하게 할 뿐 — 지금 시각은 계산할 때 시계에서(선택·수정으로 다시 계산할 때 옛 시각을 쓰지 않게)
        combine(loaded, interaction, ticker) { l, i, _ ->
            reduceTripResult((l as? Loaded.Value)?.result, l is Loaded.Value, i, clock.nowMs(), mode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripResultUiState())

    init {
        viewModelScope.launch {
            results.result(tripId).collect { r ->
                val first = loaded.value is Loaded.NotYet
                val wasEnding = current?.endsAt != null
                current = r
                loaded.value = Loaded.Value(r)
                when {
                    // 사후 확인 — 시계 없음. 목적을 아직 안 정한 운행은 바로 고를 수 있게 수정 모드로
                    first && review -> if (r != null && !r.classified) interaction.update { it.copy(editing = true) }
                    // 종료 대기로 열렸다 — 막대 전체 길이 = 지금부터 종료 시각까지
                    first && r?.endsAt != null ->
                        interaction.update { it.copy(autoCloseTotalMs = (r.endsAt - clock.nowMs()).coerceAtLeast(1)) }
                    // 이미 끝난 운행으로 열렸다 — 자체 카운트다운
                    first && r != null && r.endsAt == null -> startCountdown()
                    // 종료 대기가 풀렸다 — 끝났으면(끝난 시각이 있다) 저장, 다시 기록 중이면(기어 P를 빼고 출발해 앱 A가 종료를 취소)
                    // 저장하지 않고 떠난다. 실제 저장소엔 기록 중인 운행도 있으므로(끝난 시각 없음) 있다/없다로 가르면 진행 중인 운행에
                    // 목적을 저장하고 "저장했어요"를 띄운다(2026-11-12 점검에서 발견)
                    wasEnding && r?.endsAt == null -> if (r?.endTime == null) leave() else onEnded(r)
                }
            }
        }
        if (!review) {
            viewModelScope.launch {
                parked.distinctUntilChanged().collect {
                    parkedNow = it
                    if (!it) stopEditing()
                }
            }
        }
    }

    fun onAction(action: TripResultAction) {
        when (action) {
            TripResultAction.Continue -> keepRecording()
            is TripResultAction.Select ->
                interaction.update { if (it.editing) it.copy(selected = action.category) else it }
            // 삭제(D-39): 운행 직후 카드는 수정 모드에서, 사후 확인은 보기·수정 모두 — 지운 뒤 저장 완료 화면 없이 들어온 화면으로
            TripResultAction.Delete ->
                interaction.update { if (it.editing || review) it.copy(confirmingDelete = true) else it }
            TripResultAction.CancelDelete -> interaction.update { it.copy(confirmingDelete = false) }
            TripResultAction.ConfirmDelete -> delete()
            TripResultAction.Edit -> {
                countdown?.cancel()
                interaction.update { it.copy(editing = true, autoCloseAt = null) }
            }
            TripResultAction.EditDistance -> interaction.update {
                if (!it.editing) return@update it
                it.copy(distanceEditor = DistanceEditor(distanceDraftOf(it.editedKm ?: current?.distanceKm)))
            }
            is TripResultAction.DistanceKey -> updateDistanceDraft { pressDistanceKey(it, action.key) }
            TripResultAction.DistanceDelete -> updateDistanceDraft { it.dropLast(1) }
            TripResultAction.ConfirmDistance -> confirmDistance()
            TripResultAction.CancelDistance -> interaction.update { it.copy(distanceEditor = null) }
            // [저장하기] = 남은 시간이 다 됐을 때와 같은 끝내기. 사후 확인은 고친 목적만 저장하고 목록으로
            TripResultAction.Save -> if (review) saveReview() else finish()
            TripResultAction.Close -> leave()
            TripResultAction.CheckDrawn -> holdThenLeave()
        }
    }

    private fun updateDistanceDraft(edit: (String) -> String) = interaction.update { i ->
        i.distanceEditor?.let { e -> i.copy(distanceEditor = DistanceEditor(edit(e.draft))) } ?: i
    }

    /** 거리 팝업 [완료]: 범위 안이면 화면 거리를 바꾸고 닫는다(저장은 [저장하기] 때), 아니면 이유를 팝업에. */
    private fun confirmDistance() = interaction.update { i ->
        val editor = i.distanceEditor ?: return@update i
        when (val r = parseDistance(editor.draft)) {
            is DistanceInput.Valid -> i.copy(editedKm = r.km, distanceEditor = null)
            is DistanceInput.Invalid -> i.copy(distanceEditor = editor.copy(error = r.error))
        }
    }

    /** 고친 거리(있으면) → 목적 순으로 저장한다. 저장 경로(자동 저장·[저장하기]·사후 확인)가 모두 여기를 지난다. */
    private suspend fun persist() {
        interaction.value.editedKm?.let { results.editDistance(tripId, it) }
        results.classify(tripId, selectedNow())
    }

    private fun keepRecording() {
        if (current?.endsAt == null || finishing) return
        viewModelScope.launch {
            session.keepRecording()
            leave()
        }
    }

    private fun onEnded(result: TripResult) {
        if (deleting) return // 지우려고 끝낸 것 — 목적을 저장하면 운행이 되살아난다
        viewModelScope.launch {
            persist()
            if (finishing || !interaction.value.editing) showSaved()
        }
    }

    /** 운행을 지우고(종료 대기면 앱 A가 먼저 끝낸다) 들어온 화면으로. 한 번만. */
    private fun delete() {
        if (deleting || finishing) return
        deleting = true
        countdown?.cancel()
        interaction.update { it.copy(confirmingDelete = false) }
        viewModelScope.launch {
            session.discardTrip(tripId)
            leave()
        }
    }

    /** 정차·주행이 됐다 — 고르던 목적은 지금 선택 그대로 두고, 끝난 운행이면 5초 자동 저장을 다시 건다(종료 대기면 앱 A 종료 시각이 마감). */
    private fun stopEditing() {
        if (!interaction.value.editing || finishing || deleting) return
        // 삭제 팝업도 닫는다 — 정차·주행 중엔 누를 수 있는 것이 없다
        interaction.update { it.copy(editing = false, confirmingDelete = false, distanceEditor = null) }
        if (current?.endsAt == null) startCountdown()
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                // 마감 시각 하나만 두고 그때 끝낸다 — 막대·버튼 글자는 마감까지 남은 시간에서 계산된다
                interaction.update {
                    it.copy(autoCloseAt = clock.nowMs() + AUTO_CLOSE_MS, autoCloseTotalMs = AUTO_CLOSE_MS)
                }
                delay(AUTO_CLOSE_MS)
                finish()
            }
    }

    /** 사후 확인(D-29)의 [저장하기] — 고친 목적을 저장하고 저장 완료 화면 → 목록으로. */
    private fun saveReview() {
        val now = interaction.value
        if (!now.editing || now.saving) return
        interaction.update { it.copy(saving = true) }
        viewModelScope.launch {
            persist()
            showSaved()
        }
    }

    /** 저장을 마쳤다 — 저장 완료 화면으로. 이미 떠났으면(기록 이어가기·주행 시작) 보여 줄 것 없음. */
    private fun showSaved() {
        if (left) return
        countdown?.cancel()
        interaction.update { it.copy(saved = true, saving = false, editing = false, autoCloseAt = null) }
        // 정차·주행 중엔 체크가 정지형(바로 다 그려짐)이라 지금부터 센다 — 앱이 뒤로 물러나 화면이 그려지지 않아도
        // 저장 완료 화면이 남아 있다가 나중에 앱을 열 때 지난 운행의 체크가 뜨지 않게(D-35)
        if (!parkedNow) holdThenLeave()
    }

    /** 체크를 다 그린 뒤 [SAVED_HOLD_MS] 보여 주고 들어온 화면으로. 한 번만. */
    private fun holdThenLeave() {
        if (!interaction.value.saved || hold != null) return
        hold = viewModelScope.launch {
            delay(SAVED_HOLD_MS)
            leave()
        }
    }

    /** 지금 선택으로 저장하고 저장 완료 화면으로(→ 들어온 화면). 종료 대기 중이면 먼저 바로 끝낸다(저장은 onEnded가). */
    private fun finish() {
        if (finishing || deleting) return
        finishing = true
        countdown?.cancel()
        interaction.update { it.copy(saving = true, autoCloseAt = null) }
        viewModelScope.launch {
            if (current?.endsAt != null) {
                session.endNow()
            } else {
                persist()
                showSaved()
            }
        }
    }

    /** 들어온 화면으로(뒤가 없으면 Home). 한 번만. 이 카드만 뺀다 — 그사이 Home이 카드를 치웠으면 아무것도 닫지 않는다. */
    private fun leave() {
        if (left) return
        left = true
        navigator.close(Route.TripResult(tripId, mode))
    }

    /** 지금 선택: 고른 값, 없으면 저장된 분류·기본값. */
    private fun selectedNow() = interaction.value.selected ?: current?.category ?: uiState.value.selected

    private sealed interface Loaded {
        data object NotYet : Loaded

        data class Value(val result: TripResult?) : Loaded
    }
}
