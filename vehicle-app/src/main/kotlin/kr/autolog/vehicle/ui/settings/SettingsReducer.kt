package kr.autolog.vehicle.ui.settings

import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.data.repository.ServerLink
import kr.autolog.vehicle.data.repository.UploadInfo
import kr.autolog.vehicle.ui.trips.tripWhenOf

/** 사람이 고치는 중인 값과 진행 상태 — ViewModel이 들고 있고 [reduceSettings]가 저장소 값과 합친다. */
internal data class SettingsLocal(
    val section: SettingsSection? = null,
    val mail: MailForm = MailForm(),
    val mailConnected: Boolean = false,
    val dialog: SettingsDialog? = null,
    val running: SettingsTask? = null,
    val results: Map<SettingsTask, TaskResult> = emptyMap(),
    val overlay: OverlayUi? = null,
)

/** 저장소 값 묶음(설정 화면이 구독하는 원천). */
internal data class SettingsSources(
    val link: ServerLink,
    val upload: UploadInfo,
    val recipients: List<RecipientInfo>,
    val users: List<UserInfo>,
    val autoMonthlySend: Boolean,
)

/** 설정 상태 계산(순수 함수, SettingsReducerTest 대상). 시각은 [now] 기준 "오늘 10:24" / "11월 7일 10:24"로. */
internal fun reduceSettings(local: SettingsLocal, sources: SettingsSources, app: AppInfo, now: Long): SettingsUiState =
    SettingsUiState(
        section = local.section,
        link = when (val l = sources.link) {
            ServerLink.NotLinked -> LinkUi.NotLinked
            is ServerLink.Linked -> LinkUi.Linked(
                l.vehicleLabel,
                l.serverUrl,
                l.lastSyncAt?.let {
                    tripWhenOf(it, now)
                },
            )
            is ServerLink.Unreachable -> LinkUi.Unreachable(l.vehicleLabel, l.serverUrl)
        },
        upload = sources.upload.let {
            UploadUi(it.enabled, it.pendingCount, it.lastUploadAt?.let { at -> tripWhenOf(at, now) }, it.lastError)
        },
        mailConnected = local.mailConnected,
        mail = local.mail,
        // 서버 배포분(회사 지정) 먼저 — 지울 수 없는 것이 위에
        recipients = sources.recipients.sortedBy { it.origin != Origin.SERVER }
            .map { RecipientRowUi(it.email, it.name, it.origin == Origin.SERVER) },
        staff = sources.users.sortedBy { it.name }
            .map { StaffRowUi(it.userId, it.name, it.dept, it.email, it.origin == Origin.SERVER) },
        autoMonthlySend = sources.autoMonthlySend,
        running = local.running,
        results = local.results,
        dialog = local.dialog,
        app = app,
        overlay = local.overlay,
    )
