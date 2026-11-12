package kr.autolog.vehicle.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.core.ConfirmResult
import kr.autolog.vehicle.data.repository.FuelDraft
import kr.autolog.vehicle.data.repository.ReceiptRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

/**
 * 주유 기록 입력 창(스펙 7.5, D-20). [receiptId]가 있으면 그 영수증의 OCR 값으로, 없으면 빈 칸으로 연다.
 * 상태 계산은 `FuelEntryReducer.kt`, 여기는 Repository 호출과 화면 이동만.
 */
class FuelEntryViewModel(
    private val receiptId: String?,
    private val receipts: ReceiptRepository,
    private val session: SessionRepository,
    private val navigator: Navigator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FuelEntryUiState(loading = receiptId != null))
    val uiState: StateFlow<FuelEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value =
                if (receiptId == null) reduceManualEntry(session.state.first()) else receiptEntry(receiptId)
        }
    }

    private suspend fun receiptEntry(id: String): FuelEntryUiState {
        val receipt = receipts.receipt(id).first() ?: return FuelEntryUiState(error = FuelEntryError.ReceiptMissing)
        return reduceReceiptEntry(receipt, receipts.candidates(receipt))
    }

    fun onAction(action: FuelEntryAction) {
        when (action) {
            is FuelEntryAction.Edit -> _uiState.update { it.copy(editing = action.field) }
            is FuelEntryAction.Key -> _uiState.update { it.pressKey(action.key) }
            FuelEntryAction.Delete -> _uiState.update { it.deleteKey() }
            is FuelEntryAction.TextChanged -> _uiState.update { it.typeText(action.value) }
            FuelEntryAction.CloseEditor -> _uiState.update { it.copy(editing = null) }
            is FuelEntryAction.SelectTrip -> _uiState.update { it.copy(selectedTripId = action.tripId) }
            FuelEntryAction.Add -> add()
            FuelEntryAction.Back -> navigator.pop()
        }
    }

    private fun add() {
        val state = _uiState.value
        if (!state.canAdd) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = FuelDraft(state.receipt, state.toReceiptFields(), state.selectedTripId)
            when (val result = receipts.add(draft)) {
                is ConfirmResult.Saved -> navigator.replaceAll(Route.Home)
                is ConfirmResult.Rejected ->
                    _uiState.update { it.copy(saving = false, error = FuelEntryError.Rejected(result.reason)) }
            }
        }
    }
}
