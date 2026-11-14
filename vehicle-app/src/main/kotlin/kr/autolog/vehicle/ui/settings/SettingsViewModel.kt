package kr.autolog.vehicle.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.repository.ConnectCode
import kr.autolog.vehicle.data.repository.Outcome
import kr.autolog.vehicle.data.repository.OverlayPermissionRepository
import kr.autolog.vehicle.data.repository.SettingsRepository
import kr.autolog.vehicle.nav.Navigator

/**
 * 설정(스펙 7.9). 섹션 열기·팝업·입력 칸·작업 실행만 맡고, 표시용 계산은 [reduceSettings].
 * 작업은 한 번에 하나([SettingsLocal.running]) — 결과는 작업별로 남아 해당 섹션·팝업 아래에 한 줄로 보인다.
 * 팝업 작업(연결 · 추가 · 지우기)이 성공하면 팝업을 닫는다.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val navigator: Navigator,
    private val clock: AppClock,
    private val app: AppInfo,
    private val overlay: OverlayPermissionRepository,
) : ViewModel() {
    private val local = MutableStateFlow(
        SettingsLocal(
            overlay = if (overlay.supported) OverlayUi(allowed = overlay.isAllowed()) else null,
        ),
    )

    private val sources =
        combine(
            settings.link,
            settings.upload,
            settings.recipients(),
            settings.users(),
            combine(settings.autoMonthlySend, settings.mailAccount, ::Pair),
        ) { link, upload, recipients, users, (autoSend, mailAccount) ->
            SettingsSources(link, upload, recipients, users, autoSend, mailAccount)
        }

    val uiState: StateFlow<SettingsUiState> =
        combine(local, sources) { l, s -> reduceSettings(l, s, app, clock.nowMs()) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SettingsUiState(app = app, overlay = local.value.overlay),
            )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.Open -> local.update { it.copy(section = action.section) }
            SettingsAction.CloseSection -> local.update { it.copy(section = null) }
            SettingsAction.Back -> navigator.pop()
            SettingsAction.StartRegister ->
                openDialog(SettingsDialog.Register(), SettingsTask.REGISTER)
            SettingsAction.UploadNow -> run(SettingsTask.UPLOAD) { settings.uploadNow().toResult() }
            SettingsAction.StartAddRecipient -> openDialog(SettingsDialog.AddRecipient(), SettingsTask.ADD_RECIPIENT)
            is SettingsAction.AskRemoveRecipient -> {
                val name = uiState.value.recipients.firstOrNull { it.email == action.email }?.name
                openDialog(SettingsDialog.RemoveRecipient(action.email, name), SettingsTask.REMOVE_RECIPIENT)
            }
            SettingsAction.StartAddStaff -> openDialog(SettingsDialog.AddStaff(), SettingsTask.ADD_STAFF)
            is SettingsAction.SetAutoMonthlySend -> viewModelScope.launch {
                settings.setAutoMonthlySend(action.enabled)
            }
            SettingsAction.SyncNow -> run(SettingsTask.SYNC) { settings.syncNow().toResult() }
            is SettingsAction.EditDialog -> local.update {
                it.copy(dialog = it.dialog?.edit(action.field, action.value))
            }
            SettingsAction.SubmitDialog -> submitDialog()
            SettingsAction.DismissDialog -> local.update { if (it.running == null) it.copy(dialog = null) else it }
            // 허용됐으면 "권한 화면 없음" 안내도 지운다
            SettingsAction.RefreshOverlay -> local.update { l ->
                l.copy(overlay = l.overlay?.let { OverlayUi(allowed = overlay.isAllowed(), it.settingsUnavailable) })
            }
            SettingsAction.OpenOverlaySettings -> {
                val opened = overlay.openSettings()
                local.update { l -> l.copy(overlay = l.overlay?.copy(settingsUnavailable = !opened)) }
            }
        }
    }

    private fun openDialog(dialog: SettingsDialog, task: SettingsTask) =
        local.update { it.copy(dialog = dialog, results = it.results - task) }

    private fun submitDialog() {
        when (val d = local.value.dialog) {
            is SettingsDialog.Register -> {
                if (!ConnectCode.isComplete(d.code)) return
                run(SettingsTask.REGISTER) { settings.register(d.code).toResult() }
            }
            is SettingsDialog.AddRecipient -> {
                if (d.email.isBlank()) return
                run(SettingsTask.ADD_RECIPIENT) { settings.addRecipient(d.email, d.name.ifBlank { null }).toResult() }
            }
            is SettingsDialog.AddStaff -> {
                if (d.name.isBlank()) return
                run(SettingsTask.ADD_STAFF) { settings.addUser(d.name, d.dept, d.email).toResult() }
            }
            is SettingsDialog.RemoveRecipient ->
                run(SettingsTask.REMOVE_RECIPIENT) { settings.removeRecipient(d.email).toResult() }
            null -> Unit
        }
    }

    /** 작업 하나 실행: 진행 표시 → 결과 기록 → 성공이면 열린 팝업을 닫는다. 이미 다른 작업이 돌고 있으면 무시. */
    private fun run(task: SettingsTask, block: suspend () -> TaskResult) {
        if (local.value.running != null) return
        local.update { it.copy(running = task, results = it.results - task) }
        viewModelScope.launch {
            val result = block()
            local.update {
                it.copy(
                    running = null,
                    results = it.results + (task to result),
                    dialog = if (result == TaskResult.Done) null else it.dialog,
                )
            }
        }
    }
}

private fun Outcome.toResult(): TaskResult = when (this) {
    Outcome.Ok -> TaskResult.Done
    is Outcome.Failed -> TaskResult.Failed(reason)
}

private fun SettingsDialog.edit(field: DialogField, value: String): SettingsDialog = when (this) {
    is SettingsDialog.Register -> when (field) {
        // 키보드로 친 소문자는 대문자로, 서버가 쓰지 않는 글자는 들어가지 않는다(G-01)
        DialogField.CONNECT_CODE -> copy(code = ConnectCode.normalize(value))
        else -> this
    }
    is SettingsDialog.AddRecipient -> when (field) {
        DialogField.EMAIL -> copy(email = value)
        DialogField.NAME -> copy(name = value)
        else -> this
    }
    is SettingsDialog.AddStaff -> when (field) {
        DialogField.NAME -> copy(name = value)
        DialogField.DEPT -> copy(dept = value)
        DialogField.EMAIL -> copy(email = value)
        else -> this
    }
    is SettingsDialog.RemoveRecipient -> this
}
