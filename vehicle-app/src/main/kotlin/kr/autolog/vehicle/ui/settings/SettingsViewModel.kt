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
import kr.autolog.vehicle.core.PlaceAddResult
import kr.autolog.vehicle.core.PlaceSearchResult
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
            settings.recipients(),
            settings.users(),
            combine(
                settings.autoMonthlySend,
                settings.mailAccount,
                settings.purposes(),
                settings.purposeRules(),
                ::Extra,
            ),
        ) { link, recipients, users, e ->
            SettingsSources(link, recipients, users, e.autoSend, e.mailAccount, e.purposes, e.rules)
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
            SettingsAction.StartAddRecipient -> openDialog(SettingsDialog.AddRecipient(), SettingsTask.ADD_RECIPIENT)
            is SettingsAction.AskRemoveRecipient -> {
                val name = uiState.value.recipients.firstOrNull { it.email == action.email }?.name
                openDialog(SettingsDialog.RemoveRecipient(action.email, name), SettingsTask.REMOVE_RECIPIENT)
            }
            SettingsAction.StartAddStaff -> openDialog(SettingsDialog.AddStaff(), SettingsTask.ADD_STAFF)
            SettingsAction.StartAddPurpose -> openDialog(SettingsDialog.AddPurpose(), SettingsTask.ADD_PURPOSE)
            is SettingsAction.SetPurposeCategory -> editDialog<SettingsDialog.AddPurpose> {
                it.copy(category = action.category)
            }
            is SettingsAction.AskRemovePurpose -> {
                val p = uiState.value.purposes.firstOrNull { it.name == action.name } ?: return
                openDialog(SettingsDialog.RemovePurpose(p.name, p.label), SettingsTask.REMOVE_PURPOSE)
            }
            SettingsAction.StartAddPurposeRule -> viewModelScope.launch {
                val places = settings.places().map { PlaceChoice(it.placeId, it.label) }
                val purposes = uiState.value.purposes.map { PurposeChoice(it.name, it.label, it.category) }
                openDialog(SettingsDialog.AddPurposeRule(places, purposes), SettingsTask.ADD_PURPOSE_RULE)
            }
            is SettingsAction.PickRuleOrigin -> editDialog<SettingsDialog.AddPurposeRule> {
                it.copy(originPlaceId = action.placeId)
            }
            is SettingsAction.PickRuleDest -> editDialog<SettingsDialog.AddPurposeRule> {
                it.copy(destPlaceId = action.placeId)
            }
            is SettingsAction.PickRulePurpose -> editDialog<SettingsDialog.AddPurposeRule> {
                it.copy(purposeName = action.name)
            }
            is SettingsAction.StartAddPlace -> editDialog<SettingsDialog.AddPurposeRule> {
                SettingsDialog.AddPlace(back = it, target = action.target)
            }.also {
                local.update { l -> l.copy(results = l.results - SettingsTask.SEARCH_PLACE - SettingsTask.ADD_PLACE) }
            }
            SettingsAction.SearchPlace -> (local.value.dialog as? SettingsDialog.AddPlace)?.let { d ->
                findPlaces { settings.searchPlaces(d.query) }
            }
            SettingsAction.UseCurrentPlace -> findPlaces { settings.currentPlace() }
            is SettingsAction.PickCandidate -> editDialog<SettingsDialog.AddPlace> { d ->
                val c = d.candidates?.getOrNull(action.index) ?: return@editDialog d
                d.copy(picked = action.index, label = c.name ?: d.label)
            }
            is SettingsAction.AskRemovePurposeRule -> {
                val r = uiState.value.purposeRules.firstOrNull { it.ruleId == action.ruleId } ?: return
                openDialog(SettingsDialog.RemovePurposeRule(r), SettingsTask.REMOVE_PURPOSE_RULE)
            }
            is SettingsAction.SetAutoMonthlySend -> viewModelScope.launch {
                settings.setAutoMonthlySend(action.enabled)
            }
            SettingsAction.SyncNow -> run(SettingsTask.SYNC) { settings.syncNow().toResult() }
            is SettingsAction.EditDialog -> local.update {
                it.copy(dialog = it.dialog?.edit(action.field, action.value))
            }
            SettingsAction.SubmitDialog -> submitDialog()
            // 새 장소 팝업의 X는 규칙 팝업으로 돌아간다
            SettingsAction.DismissDialog -> local.update {
                when {
                    it.running != null -> it
                    it.dialog is SettingsDialog.AddPlace -> it.copy(
                        dialog = (it.dialog as SettingsDialog.AddPlace).back,
                    )
                    else -> it.copy(dialog = null)
                }
            }
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

    private inline fun <reified D : SettingsDialog> editDialog(crossinline f: (D) -> SettingsDialog) =
        local.update { l -> (l.dialog as? D)?.let { l.copy(dialog = f(it)) } ?: l }

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
            is SettingsDialog.AddPurpose -> {
                if (d.label.isBlank()) return
                run(SettingsTask.ADD_PURPOSE) { settings.addPurpose(d.label, d.category).toResult() }
            }
            is SettingsDialog.RemovePurpose ->
                run(SettingsTask.REMOVE_PURPOSE) { settings.removePurpose(d.name).toResult() }
            is SettingsDialog.AddPurposeRule -> {
                val dest = d.destPlaceId ?: return
                val purpose = d.purposeName ?: return
                if (!d.complete) return
                run(SettingsTask.ADD_PURPOSE_RULE) {
                    settings.addPurposeRule(d.originPlaceId, dest, purpose).toResult()
                }
            }
            is SettingsDialog.AddPlace -> addPlace(d)
            is SettingsDialog.RemovePurposeRule ->
                run(SettingsTask.REMOVE_PURPOSE_RULE) { settings.removePurposeRule(d.rule.ruleId).toResult() }
            null -> Unit
        }
    }

    /** 새 장소 후보 찾기(검색·지금 위치). 팝업은 닫지 않고 후보만 바꾼다. */
    private fun findPlaces(block: suspend () -> PlaceSearchResult) = work(SettingsTask.SEARCH_PLACE) {
        when (val r = block()) {
            is PlaceSearchResult.Found -> {
                val list = r.candidates.map { PlaceCandidateUi(it.name, it.address, it.lat, it.lng) }
                // 후보가 하나뿐(지금 위치)이면 바로 고른다
                editDialog<SettingsDialog.AddPlace> {
                    it.copy(
                        candidates = list,
                        picked = if (list.size ==
                            1
                        ) {
                            0
                        } else {
                            null
                        },
                        label = list.singleOrNull()?.name ?: it.label,
                    )
                }
                TaskResult.Done
            }
            is PlaceSearchResult.Failed -> TaskResult.Failed(r.reason)
        }
    }

    /** 새 장소 저장 → 규칙 팝업으로 돌아가 그 장소를 고른다. */
    private fun addPlace(d: SettingsDialog.AddPlace) {
        val c = d.picked?.let { d.candidates?.getOrNull(it) } ?: return
        if (d.label.isBlank()) return
        work(SettingsTask.ADD_PLACE) {
            when (val r = settings.addPlace(d.label, c.lat, c.lng)) {
                is PlaceAddResult.Added -> {
                    val places = settings.places().map { PlaceChoice(it.placeId, it.label) }
                    val back = d.back.copy(places = places)
                    local.update {
                        it.copy(
                            dialog = when (d.target) {
                                RuleTarget.ORIGIN -> back.copy(originPlaceId = r.placeId)
                                RuleTarget.DEST -> back.copy(destPlaceId = r.placeId)
                            },
                        )
                    }
                    TaskResult.Done
                }
                is PlaceAddResult.Invalid -> TaskResult.Failed(r.reason)
            }
        }
    }

    /** 팝업을 닫지 않는 작업(검색처럼 팝업 안에서 이어 가는 것). */
    private fun work(task: SettingsTask, block: suspend () -> TaskResult) {
        if (local.value.running != null) return
        local.update { it.copy(running = task, results = it.results - task) }
        viewModelScope.launch {
            val result = block()
            local.update { it.copy(running = null, results = it.results + (task to result)) }
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
    is SettingsDialog.AddPlace -> when (field) {
        DialogField.QUERY -> copy(query = value)
        DialogField.NAME -> copy(label = value)
        else -> this
    }
    is SettingsDialog.AddPurpose -> when (field) {
        DialogField.NAME -> copy(label = value)
        else -> this
    }
    is SettingsDialog.RemoveRecipient,
    is SettingsDialog.RemovePurpose,
    is SettingsDialog.AddPurposeRule,
    is SettingsDialog.RemovePurposeRule,
    -> this
}

/** 설정 원천 중 combine 칸(5개)을 넘는 것들. */
private data class Extra(
    val autoSend: Boolean,
    val mailAccount: String?,
    val purposes: List<kr.autolog.vehicle.core.PurposeInfo>,
    val rules: List<kr.autolog.vehicle.core.PurposeRuleInfo>,
)
