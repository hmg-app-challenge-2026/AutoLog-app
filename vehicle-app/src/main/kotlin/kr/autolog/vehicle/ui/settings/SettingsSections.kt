package kr.autolog.vehicle.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ButtonNote
import kr.autolog.vehicle.ui.design.components.CheckRow
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.RoundIconButton
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.motion.ExpandSwap
import kr.autolog.vehicle.ui.trips.tripWhenText

/** 열린 섹션의 내용(스펙 7.9 순서). 위에서 아래로: 지금 상태 → 고칠 칸·목록 → 버튼 → 마지막 결과 한 줄. */
@Composable
internal fun SectionDetail(section: SettingsSection, state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        when (section) {
            SettingsSection.VEHICLE -> Vehicle(state, onAction)
            SettingsSection.SERVER -> Server(state, onAction)
            SettingsSection.MAIL -> Mail(state)
            SettingsSection.RECIPIENTS -> Recipients(state, onAction)
            SettingsSection.STAFF -> Staff(state, onAction)
            SettingsSection.AUTOMATION -> Automation(state, onAction)
            SettingsSection.SYNC -> Sync(state, onAction)
            SettingsSection.ABOUT -> About(state)
        }
    }
}

/** 1. 차량 정보 — 연결 상태 카드 + [연결 코드 입력]/[다시 연결](팝업: 6자리 코드) + (실제 앱) 운행 기록 알약 권한 카드. */
@Composable
private fun Vehicle(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val c = AutoLogTheme.colors
    when (val l = state.link) {
        LinkUi.NotLinked -> StatusCard(
            stringResource(R.string.settings_not_linked),
            c.textSecondary,
            listOf(stringResource(R.string.settings_vehicle_hint)),
        )
        is LinkUi.Linked -> StatusCard(
            stringResource(R.string.settings_linked),
            c.success,
            listOf(stringResource(R.string.settings_vehicle_plate, l.vehicle)),
        )
        is LinkUi.Unreachable -> StatusCard(
            stringResource(R.string.settings_unreachable),
            c.warning,
            listOf(stringResource(R.string.settings_vehicle_plate, l.vehicle)),
        )
    }
    if (state.link == LinkUi.NotLinked) {
        PrimaryButton(
            stringResource(R.string.settings_register),
            onClick = { onAction(SettingsAction.StartRegister) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        SecondaryButton(
            stringResource(R.string.settings_register_again),
            onClick = { onAction(SettingsAction.StartRegister) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ResultLine(state, SettingsTask.REGISTER)
    state.overlay?.let { RecordingPermission(it, onAction) }
}

/**
 * 운행 기록 알약(스펙 6.11)의 화면 위 표시 권한 카드 — 실제 앱만. 시스템 권한 화면에서 돌아오면 다시 확인한다.
 * 이 차량에 권한 화면이 없으면 버튼 위에 그 사실과 운행 알림 안내 한 줄.
 */
@Composable
private fun RecordingPermission(overlay: OverlayUi, onAction: (SettingsAction) -> Unit) {
    val c = AutoLogTheme.colors
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAction(SettingsAction.RefreshOverlay) }
    StatusCard(
        stringResource(
            if (overlay.allowed) R.string.settings_recording_pill_on else R.string.settings_recording_pill_off,
        ),
        if (overlay.allowed) c.success else c.warning,
        listOf(stringResource(R.string.settings_recording_pill_hint)),
    )
    if (!overlay.allowed) {
        if (overlay.settingsUnavailable) ButtonNote(stringResource(R.string.settings_recording_pill_unavailable))
        SecondaryButton(
            stringResource(R.string.settings_recording_pill_allow),
            onClick = { onAction(SettingsAction.OpenOverlaySettings) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 2. 회사 정보(사내 서버, 8장) — 주소 · 상태 · 전송 대기 · 마지막 전송 + [지금 전송]. 차량을 연결하기 전엔 [차량 정보로] 안내. */
@Composable
private fun Server(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val link = state.link
    if (link == LinkUi.NotLinked) {
        NeedsLink(onAction)
        return
    }
    val c = AutoLogTheme.colors
    val url = (link as? LinkUi.Linked)?.serverUrl ?: (link as LinkUi.Unreachable).serverUrl
    InfoCard(
        listOf(
            stringResource(R.string.settings_server_url) to url,
            stringResource(R.string.settings_server_state) to
                stringResource(if (link is LinkUi.Linked) R.string.settings_linked else R.string.settings_unreachable),
            stringResource(R.string.settings_upload_pending_label) to
                stringResource(R.string.settings_count, state.upload.pending),
            stringResource(R.string.settings_last_upload) to
                (state.upload.lastUpload?.let { tripWhenText(it) } ?: stringResource(R.string.settings_never)),
        ),
    )
    state.upload.lastError?.let { ButtonNote(it, style = AutoLogTheme.type.caption, color = c.danger) }
    PrimaryButton(
        stringResource(R.string.settings_upload_now),
        onClick = { onAction(SettingsAction.UploadNow) },
        modifier = Modifier.fillMaxWidth(),
        loading = state.running == SettingsTask.UPLOAD,
    )
    ResultLine(state, SettingsTask.UPLOAD)
}

/**
 * 3. 메일 — 연결된 발신 메일 주소만 보여 준다. 계정은 회사 관리자 웹 [메일 설정]에서 정해 서버로 온다(2026-11-14 사용자 결정:
 * 메일 계정은 하나, 차에서는 입력하지 않는다).
 */
@Composable
private fun Mail(state: SettingsUiState) {
    val c = AutoLogTheme.colors
    Text(
        stringResource(
            if (state.mailConnected) R.string.settings_mail_connected else R.string.settings_mail_not_connected,
        ),
        style = AutoLogTheme.type.body,
        color = if (state.mailConnected) c.success else c.textSecondary,
    )
    state.mailAccount?.let { Text(it, style = AutoLogTheme.type.body, color = c.textPrimary) }
    Text(
        stringResource(R.string.settings_mail_managed_note),
        style = AutoLogTheme.type.caption,
        color = c.textSecondary,
    )
}

/** 4. 받는 사람 — 회사 지정(잠금, 못 지움) 먼저 + 직접 추가(지우기 1회 확인) + [받는 사람 추가]. */
@Composable
private fun Recipients(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val company = stringResource(R.string.settings_from_company)
    val separator = stringResource(R.string.meta_separator)
    state.recipients.forEach { r ->
        PersonRow(
            primary = r.name ?: r.email,
            // 회사 지정은 아래 줄 앞에. 회사 지정도 이 차에서는 뺄 수 있다(2026-11-14 사용자 결정)
            secondary = listOfNotNull(company.takeIf { r.fromServer }, r.email.takeIf { r.name != null })
                .joinToString(separator).ifEmpty { null },
            locked = false,
            onRemove = { onAction(SettingsAction.AskRemoveRecipient(r.email)) },
        )
    }
    SecondaryButton(
        stringResource(R.string.settings_add_recipient),
        onClick = { onAction(SettingsAction.StartAddRecipient) },
        modifier = Modifier.fillMaxWidth(),
    )
    ResultLine(state, SettingsTask.ADD_RECIPIENT)
    ResultLine(state, SettingsTask.REMOVE_RECIPIENT)
}

/** 5. 직원 — 목록(이름 · 부서 · 이메일) + [직원 추가]. 회사 지정 직원은 잠금 표시. */
@Composable
private fun Staff(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val company = stringResource(R.string.settings_from_company)
    val separator = stringResource(R.string.meta_separator)
    state.staff.forEach { s ->
        PersonRow(
            primary = s.name,
            secondary = listOfNotNull(
                company.takeIf {
                    s.fromServer
                },
                s.dept,
                s.email,
            ).joinToString(separator).ifEmpty { null },
            locked = s.fromServer,
            onRemove = null,
        )
    }
    SecondaryButton(
        stringResource(R.string.settings_add_staff),
        onClick = { onAction(SettingsAction.StartAddStaff) },
        modifier = Modifier.fillMaxWidth(),
    )
    ResultLine(state, SettingsTask.ADD_STAFF)
}

/** 6. 자동화 — 월말 운행기록부 자동 생성·발송(체크 행 하나). 메일 계정이 없으면 보낼 수 없다는 안내. */
@Composable
private fun Automation(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    CheckRow(
        stringResource(R.string.settings_auto_send),
        checked = state.autoMonthlySend,
        onClick = { onAction(SettingsAction.SetAutoMonthlySend(!state.autoMonthlySend)) },
        detail = stringResource(R.string.settings_auto_send_detail),
    )
    if (state.autoMonthlySend && !state.mailConnected) {
        Text(
            stringResource(R.string.settings_auto_send_needs_mail),
            style = AutoLogTheme.type.caption,
            color = AutoLogTheme.colors.warning,
        )
    }
}

/** 7. 동기화 — 회사 서버에서 직원·받는 사람·장소 목록을 받는다. [지금 동기화] + 마지막 동기화 시각. */
@Composable
private fun Sync(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val link = state.link
    if (link == LinkUi.NotLinked) {
        NeedsLink(onAction)
        return
    }
    Text(
        stringResource(R.string.settings_sync_summary),
        style = AutoLogTheme.type.body,
        color = AutoLogTheme.colors.textSecondary,
    )
    (link as? LinkUi.Linked)?.lastSync?.let {
        InfoCard(listOf(stringResource(R.string.settings_last_sync_label) to tripWhenText(it)))
    }
    PrimaryButton(
        stringResource(R.string.settings_sync_now),
        onClick = { onAction(SettingsAction.SyncNow) },
        modifier = Modifier.fillMaxWidth(),
        loading = state.running == SettingsTask.SYNC,
    )
    ResultLine(state, SettingsTask.SYNC)
}

/** 8. 정보 — 버전(데모면 표시) · 오픈소스 라이선스. */
@Composable
private fun About(state: SettingsUiState) {
    val version =
        if (state.app.demo) {
            stringResource(R.string.settings_version_demo, state.app.version)
        } else {
            state.app.version
        }
    InfoCard(listOf(stringResource(R.string.settings_version_label) to version))
    Text(
        stringResource(R.string.settings_licenses),
        style = AutoLogTheme.type.section,
        color = AutoLogTheme.colors.textSecondary,
    )
    InfoCard(
        listOf(
            stringResource(R.string.license_asta_sans) to stringResource(R.string.license_ofl),
            stringResource(R.string.license_androidx) to stringResource(R.string.license_apache),
            stringResource(R.string.license_zxing) to stringResource(R.string.license_apache),
            stringResource(R.string.license_coil) to stringResource(R.string.license_apache),
        ),
    )
}

/** 차량을 연결하기 전엔 쓸 수 없는 섹션: 이유 한 줄 + [차량 연결로]. */
@Composable
private fun NeedsLink(onAction: (SettingsAction) -> Unit) {
    ButtonNote(stringResource(R.string.settings_needs_link))
    SecondaryButton(
        stringResource(R.string.settings_go_vehicle),
        onClick = { onAction(SettingsAction.Open(SettingsSection.VEHICLE)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 상태 카드: 큰 상태 한 줄(점 색과 같은 색) + 보조 줄들. */
@Composable
private fun StatusCard(title: String, tone: Color, lines: List<String>) {
    val c = AutoLogTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(c.surface1).padding(Space.s5),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Text(title, style = AutoLogTheme.type.title, color = tone)
        lines.forEach { Text(it, style = AutoLogTheme.type.body, color = c.textSecondary) }
    }
}

/** 이름–값 줄들을 카드 하나에(왼쪽 이름 `caption`, 오른쪽 값 `body`). */
@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    val c = AutoLogTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(c.surface1).padding(Space.s5),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        rows.forEach { (label, value) ->
            // 이름은 한 줄 그대로, 값이 남는 폭을 쓰고 오른쪽 끝에 맞춘다(긴 서버 주소도 이름을 밀지 않게)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s5),
            ) {
                Text(label, style = AutoLogTheme.type.caption, color = c.textSecondary, maxLines = 1)
                Text(
                    value,
                    Modifier.weight(1f),
                    style = AutoLogTheme.type.body,
                    color = c.textPrimary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 사람 한 줄(받는 사람·직원). [locked] = 회사 지정(오른쪽 자물쇠, 못 지움 — "회사 지정"은 [secondary] 앞에). [onRemove]가 있으면 오른쪽 지우기. */
@Composable
private fun PersonRow(primary: String, secondary: String?, locked: Boolean, onRemove: (() -> Unit)?) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surface1)
            .padding(start = Space.s5, end = Space.s2, top = Space.s2, bottom = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(primary, style = t.body, color = c.textPrimary)
            secondary?.let {
                Text(it, style = t.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            locked -> Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.settings_from_company),
                tint = c.textTertiary,
                modifier = Modifier.padding(end = Space.s4).size(Size.iconSm),
            )
            onRemove != null -> RoundIconButton(Icons.Rounded.Delete, stringResource(R.string.action_remove), onRemove)
        }
    }
}

/** 작업의 마지막 결과 한 줄 — 성공은 `success`, 실패는 `danger`. 결과가 없으면 그리지 않는다. */
@Composable
internal fun ResultLine(state: SettingsUiState, task: SettingsTask) {
    val c = AutoLogTheme.colors
    val line = when (val r = state.results[task]) {
        null -> null
        TaskResult.Done -> stringResource(doneText(task)) to c.success
        is TaskResult.Failed -> r.reason to c.danger
    }
    // 결과가 생기면 펼쳐지고, 바뀌면 그 자리에서 교체되고, 사라지면 접힌다
    ExpandSwap(line) { (text, color) -> Text(text, style = AutoLogTheme.type.body, color = color) }
}

private fun doneText(task: SettingsTask): Int = when (task) {
    SettingsTask.REGISTER -> R.string.settings_done_register
    SettingsTask.UPLOAD -> R.string.settings_done_upload
    SettingsTask.ADD_RECIPIENT -> R.string.settings_done_add_recipient
    SettingsTask.REMOVE_RECIPIENT -> R.string.settings_done_remove_recipient
    SettingsTask.ADD_STAFF -> R.string.settings_done_add_staff
    SettingsTask.SYNC -> R.string.settings_done_sync
}
