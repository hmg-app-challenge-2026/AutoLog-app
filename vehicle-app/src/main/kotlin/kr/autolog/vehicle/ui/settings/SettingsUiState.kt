package kr.autolog.vehicle.ui.settings

import kr.autolog.vehicle.core.MailError
import kr.autolog.vehicle.ui.trips.TripWhen

/** 설정 섹션(스펙 7.9) — 선언 순서 = 목록 순서. 이름·아이콘·요약 문구는 화면이 정한다. */
enum class SettingsSection { VEHICLE, SERVER, MAIL, RECIPIENTS, STAFF, AUTOMATION, SYNC, ABOUT }

/** 연결 코드 자릿수(앱 A `DeviceApi.register` 규칙). */

/**
 * 설정 화면 상태(스펙 7.9, F-24·F-01·8장). 저장소 값은 [reduceSettings]가 표시용으로 바꾸고,
 * 사람이 고치는 중인 값(메일 칸·팝업 칸)과 진행 중 작업·결과는 [SettingsLocal]에 있다.
 */
data class SettingsUiState(
    /** 열린 섹션. null = 목록만(좁은·세로 창). 넓은 창은 null이면 첫 섹션을 오른쪽에 연다. */
    val section: SettingsSection? = null,
    val link: LinkUi = LinkUi.NotLinked,
    val upload: UploadUi = UploadUi(),
    val mailConnected: Boolean = false,
    val mail: MailForm = MailForm(),
    val recipients: List<RecipientRowUi> = emptyList(),
    val staff: List<StaffRowUi> = emptyList(),
    val autoMonthlySend: Boolean = false,
    /** 지금 진행 중인 작업(버튼 로딩). 한 번에 하나. */
    val running: SettingsTask? = null,
    /** 작업별 마지막 결과(섹션·팝업 아래 한 줄). */
    val results: Map<SettingsTask, TaskResult> = emptyMap(),
    val dialog: SettingsDialog? = null,
    val app: AppInfo = AppInfo("", demo = false),
    /** 운행 기록 알약 권한 카드(차량 정보 섹션). null = 이 빌드에 알약이 없음(데모). */
    val overlay: OverlayUi? = null,
)

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

data class UploadUi(
    val enabled: Boolean = false,
    val pending: Int = 0,
    val lastUpload: TripWhen? = null,
    val lastError: String? = null,
)

/** 메일 발신 계정 입력 칸 — 사람이 고치는 글자 그대로. 비밀번호는 화면에서 항상 가린다. */
data class MailForm(
    val host: String = "",
    val port: String = "587",
    val username: String = "",
    val password: String = "",
) {
    val complete: Boolean
        get() = host.isNotBlank() && username.isNotBlank() && password.isNotBlank() && port.toIntOrNull() != null
}

data class RecipientRowUi(val email: String, val name: String?, val fromServer: Boolean)

data class StaffRowUi(
    val userId: String,
    val name: String,
    val dept: String?,
    val email: String?,
    val fromServer: Boolean,
)

data class AppInfo(val version: String, val demo: Boolean)

/** 결과를 남기는 작업. */
enum class SettingsTask { REGISTER, UPLOAD, MAIL, ADD_RECIPIENT, REMOVE_RECIPIENT, ADD_STAFF, SYNC }

sealed interface TaskResult {
    data object Done : TaskResult

    /** [reason]은 원천이 준 문장 그대로. */
    data class Failed(val reason: String) : TaskResult

    data class MailFailed(val error: MailError) : TaskResult
}

/** 설정의 팝업(모두 `ModalCard` — 오른쪽 위 X로 닫는다, D-30). */
sealed interface SettingsDialog {
    /** 차량 연결: 서버 주소 + 연결 코드 6자리(`CodeField` — 키보드 입력, `ConnectCode` 규칙). */
    data class Register(val code: String = "") : SettingsDialog

    data class AddRecipient(val email: String = "", val name: String = "") : SettingsDialog

    data class AddStaff(val name: String = "", val dept: String = "", val email: String = "") : SettingsDialog

    /** 직접 추가한 받는 사람 지우기 — 되돌릴 수 없어 한 번 묻는다. */
    data class RemoveRecipient(val email: String, val name: String?) : SettingsDialog
}

/** 팝업 입력 칸 이름 — 칸 하나를 바꾸는 액션을 하나로 쓰기 위해. */
enum class DialogField { CONNECT_CODE, EMAIL, NAME, DEPT }

enum class MailField { HOST, PORT, USERNAME, PASSWORD }

sealed interface SettingsAction {
    data class Open(val section: SettingsSection) : SettingsAction

    /** 좁은·세로 창: 상세 → 목록. */
    data object CloseSection : SettingsAction

    /** 설정 화면을 나간다. */
    data object Back : SettingsAction

    data object StartRegister : SettingsAction

    data object UploadNow : SettingsAction

    data class EditMail(val field: MailField, val value: String) : SettingsAction

    data object SaveMail : SettingsAction

    data object StartAddRecipient : SettingsAction

    data class AskRemoveRecipient(val email: String) : SettingsAction

    data object StartAddStaff : SettingsAction

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
