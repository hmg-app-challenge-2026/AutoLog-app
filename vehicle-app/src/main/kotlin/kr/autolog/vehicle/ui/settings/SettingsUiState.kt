package kr.autolog.vehicle.ui.settings

import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.ui.trips.TripWhen

/** 설정 섹션(스펙 7.9) — 선언 순서 = 목록 순서. 이름·아이콘·요약 문구는 화면이 정한다. */
enum class SettingsSection { VEHICLE, MAIL, RECIPIENTS, PURPOSES, STAFF, AUTOMATION, SYNC, ABOUT }

/** 연결 코드 자릿수(앱 A `DeviceApi.register` 규칙). */

/**
 * 설정 화면 상태(스펙 7.9, F-24·F-01·8장). 저장소 값은 [reduceSettings]가 표시용으로 바꾸고,
 * 사람이 고치는 중인 값(팝업 칸)과 진행 중 작업·결과는 [SettingsLocal]에 있다.
 */
data class SettingsUiState(
    /** 열린 섹션. null = 목록만(좁은·세로 창). 넓은 창은 null이면 첫 섹션을 오른쪽에 연다. */
    val section: SettingsSection? = null,
    val link: LinkUi = LinkUi.NotLinked,
    /** 연결된 발신 메일 주소. 관리자 웹에서 정해 서버로 온다(차에서는 입력하지 않는다). null = 아직 없음. */
    val mailAccount: String? = null,
    val recipients: List<RecipientRowUi> = emptyList(),
    val staff: List<StaffRowUi> = emptyList(),
    /** 목적 목록(기본 → 회사 지정 → 이 차). */
    val purposes: List<PurposeRowUi> = emptyList(),
    /** 출발→도착 목적 규칙(회사 지정 먼저). */
    val purposeRules: List<PurposeRuleRowUi> = emptyList(),
    val autoMonthlySend: Boolean = false,
    /** 지금 진행 중인 작업(버튼 로딩). 한 번에 하나. */
    val running: SettingsTask? = null,
    /** 작업별 마지막 결과(섹션·팝업 아래 한 줄). */
    val results: Map<SettingsTask, TaskResult> = emptyMap(),
    val dialog: SettingsDialog? = null,
    val app: AppInfo = AppInfo("", demo = false),
    /** 운행 기록 알약 권한 카드(차량 정보 섹션). null = 이 빌드에 알약이 없음(데모). */
    val overlay: OverlayUi? = null,
) {
    val mailConnected: Boolean get() = mailAccount != null
}

/**
 * 운행 기록 알약(스펙 6.11)의 화면 위 표시 권한. [settingsUnavailable] = [화면 위 표시 권한 허용]을 눌렀지만
 * 이 차량에 시스템 권한 화면이 없었다(버튼 위에 안내 한 줄).
 */
data class OverlayUi(val allowed: Boolean, val settingsUnavailable: Boolean = false)

/** 차량·사내 서버 연결(8장). [lastSync]는 마지막 동기화 시각. */
sealed interface LinkUi {
    data object NotLinked : LinkUi

    data class Linked(val vehicle: String, val serverUrl: String, val lastSync: TripWhen?) : LinkUi

    data class Unreachable(val vehicle: String, val serverUrl: String) : LinkUi
}

data class RecipientRowUi(val email: String, val name: String?, val fromServer: Boolean)

data class StaffRowUi(val userId: String, val name: String, val dept: String?, val fromServer: Boolean)

/** 목적 한 줄. [name]은 저장소 키(지울 때 씀). [builtIn]이면 지울 수 없다. */
data class PurposeRowUi(
    val name: String,
    val label: String,
    val category: PurposeCategory,
    val builtIn: Boolean,
    val fromServer: Boolean,
)

/** 출발→도착 목적 규칙 한 줄. [originLabel] null = 어디서든. 장소가 지워졌으면 [originMissing]·[destLabel] null. */
data class PurposeRuleRowUi(
    val ruleId: String,
    val originLabel: String?,
    val originMissing: Boolean,
    val destLabel: String?,
    val purposeLabel: String,
    val fromServer: Boolean,
)

/** 새 장소 후보(검색 결과·지금 위치). [name] null = 지금 위치(사람이 이름을 붙인다). */
data class PlaceCandidateUi(val name: String?, val address: String?, val lat: Double, val lng: Double)

/** [+ 새 장소]를 누른 곳 — 만든 장소를 출발과 도착 중 어디에 넣을지. */
enum class RuleTarget { ORIGIN, DEST }

/** 규칙 추가 팝업에서 고르는 장소. */
data class PlaceChoice(val placeId: String, val label: String)

/** 규칙 추가 팝업에서 고르는 목적. */
data class PurposeChoice(val name: String, val label: String, val category: PurposeCategory)

data class AppInfo(val version: String, val demo: Boolean)

/** 결과를 남기는 작업. */
enum class SettingsTask {
    REGISTER,
    ADD_RECIPIENT,
    REMOVE_RECIPIENT,
    ADD_PURPOSE,
    REMOVE_PURPOSE,
    ADD_PURPOSE_RULE,
    REMOVE_PURPOSE_RULE,
    SEARCH_PLACE,
    ADD_PLACE,
    ADD_STAFF,
    SYNC,
}

sealed interface TaskResult {
    data object Done : TaskResult

    /** [reason]은 원천이 준 문장 그대로. */
    data class Failed(val reason: String) : TaskResult
}

/** 설정의 팝업(모두 `ModalCard` — 오른쪽 위 X로 닫는다, D-30). */
sealed interface SettingsDialog {
    /** 차량 연결: 서버 주소 + 연결 코드 6자리(`CodeField` — 키보드 입력, `ConnectCode` 규칙). */
    data class Register(val code: String = "") : SettingsDialog

    data class AddRecipient(val email: String = "", val name: String = "") : SettingsDialog

    data class AddStaff(val name: String = "", val dept: String = "") : SettingsDialog

    /** 직접 추가한 받는 사람 지우기 — 되돌릴 수 없어 한 번 묻는다. */
    data class RemoveRecipient(val email: String, val name: String?) : SettingsDialog

    /** 목적 추가: 이름 + 운행기록부 구분(일반 업무용·출퇴근용·비업무용). */
    data class AddPurpose(val label: String = "", val category: PurposeCategory = PurposeCategory.BUSINESS) :
        SettingsDialog

    data class RemovePurpose(val name: String, val label: String) : SettingsDialog

    /**
     * 출발→도착 규칙 추가: 출발(어디서든 또는 장소) · 도착(장소) · 목적을 고른다.
     * [originPlaceId] null = 어디서든. 도착·목적을 다 골라야 [추가]가 켜진다.
     */
    data class AddPurposeRule(
        val places: List<PlaceChoice>,
        val purposes: List<PurposeChoice>,
        val originPlaceId: String? = null,
        val destPlaceId: String? = null,
        val purposeName: String? = null,
    ) : SettingsDialog {
        val complete: Boolean get() = destPlaceId != null && purposeName != null && destPlaceId != originPlaceId
    }

    data class RemovePurposeRule(val rule: PurposeRuleRowUi) : SettingsDialog

    /**
     * 규칙 팝업의 [+ 새 장소]: 이름·주소로 찾거나 [지금 위치] → 후보 하나 고르고 이름 붙여 [추가].
     * 추가하면(또는 X) [back] 규칙 팝업으로 돌아가고, 만든 장소가 [target]에 골라져 있다.
     */
    data class AddPlace(
        val back: AddPurposeRule,
        val target: RuleTarget,
        val query: String = "",
        val candidates: List<PlaceCandidateUi>? = null,
        val picked: Int? = null,
        val label: String = "",
    ) : SettingsDialog {
        val complete: Boolean get() = picked != null && label.isNotBlank()
    }
}

/** 팝업 입력 칸 이름 — 칸 하나를 바꾸는 액션을 하나로 쓰기 위해. */
enum class DialogField { CONNECT_CODE, EMAIL, NAME, DEPT, QUERY }

sealed interface SettingsAction {
    data class Open(val section: SettingsSection) : SettingsAction

    /** 좁은·세로 창: 상세 → 목록. */
    data object CloseSection : SettingsAction

    /** 설정 화면을 나간다. */
    data object Back : SettingsAction

    data object StartRegister : SettingsAction

    data object StartAddRecipient : SettingsAction

    data class AskRemoveRecipient(val email: String) : SettingsAction

    data object StartAddStaff : SettingsAction

    data object StartAddPurpose : SettingsAction

    data class SetPurposeCategory(val category: PurposeCategory) : SettingsAction

    data class AskRemovePurpose(val name: String) : SettingsAction

    data object StartAddPurposeRule : SettingsAction

    /** 규칙 팝업: 출발 고르기. null = 어디서든. */
    data class PickRuleOrigin(val placeId: String?) : SettingsAction

    data class PickRuleDest(val placeId: String) : SettingsAction

    data class PickRulePurpose(val name: String) : SettingsAction

    data class AskRemovePurposeRule(val ruleId: String) : SettingsAction

    /** 규칙 팝업: [+ 새 장소]. */
    data class StartAddPlace(val target: RuleTarget) : SettingsAction

    data object SearchPlace : SettingsAction

    data object UseCurrentPlace : SettingsAction

    data class PickCandidate(val index: Int) : SettingsAction

    data class SetAutoMonthlySend(val enabled: Boolean) : SettingsAction

    data object SyncNow : SettingsAction

    data class EditDialog(val field: DialogField, val value: String) : SettingsAction

    /** 팝업의 주 버튼(연결 · 추가 · 지우기). */
    data object SubmitDialog : SettingsAction

    data object DismissDialog : SettingsAction

    /** 화면이 다시 보일 때(시스템 권한 화면에서 돌아옴) 화면 위 표시 권한을 다시 확인한다. */
    data object RefreshOverlay : SettingsAction

    /** [화면 위 표시 권한 허용] — 시스템 권한 화면을 연다. */
    data object OpenOverlaySettings : SettingsAction
}
