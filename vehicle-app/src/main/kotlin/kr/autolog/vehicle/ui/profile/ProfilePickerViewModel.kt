package kr.autolog.vehicle.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.data.repository.AddDriverResult
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

class ProfilePickerViewModel(
    private val session: SessionRepository,
    private val profile: ProfileRepository,
    private val navigator: Navigator,
) : ViewModel() {
    private val showAll = MutableStateFlow(false)
    private val addForm = MutableStateFlow<AddDriverForm?>(null)

    val uiState: StateFlow<ProfilePickerUiState> =
        combine(profile.orderedProfiles(), showAll, addForm) { p, s, f ->
            ProfilePickerUiState(profiles = p, showAll = s, addForm = f, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilePickerUiState())

    fun onAction(action: ProfilePickerAction) {
        when (action) {
            is ProfilePickerAction.Select -> start(action.userId)
            ProfilePickerAction.Later -> start(null)
            ProfilePickerAction.ShowAll -> showAll.value = true
            ProfilePickerAction.HideAll -> showAll.value = false
            ProfilePickerAction.OpenAdd -> addForm.value = AddDriverForm()
            ProfilePickerAction.CancelAdd -> addForm.value = null
            // 입력을 고치면 이전 오류는 지운다
            is ProfilePickerAction.AddNameChanged -> addForm.update { it?.copy(name = action.value, error = null) }
            is ProfilePickerAction.AddDeptChanged -> addForm.update { it?.copy(dept = action.value, error = null) }
            ProfilePickerAction.SubmitAdd -> submitAdd()
        }
    }

    private fun submitAdd() {
        val form = addForm.value?.takeIf { it.canSubmit } ?: return
        addForm.value = form.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val r = profile.addDriver(form.name, form.dept)) {
                is AddDriverResult.Added -> {
                    addForm.value = null
                    start(r.userId)
                }
                is AddDriverResult.Failure -> addForm.value = form.copy(error = r, saving = false)
            }
        }
    }

    private fun start(userId: String?) {
        viewModelScope.launch {
            session.selectDriver(userId)
            navigator.replaceAll(Route.Home)
        }
    }
}
