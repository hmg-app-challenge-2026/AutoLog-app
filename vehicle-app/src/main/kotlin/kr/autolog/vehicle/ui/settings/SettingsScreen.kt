package kr.autolog.vehicle.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.NavRow
import kr.autolog.vehicle.ui.design.components.ScreenHeader
import kr.autolog.vehicle.ui.design.components.ScrollColumn
import kr.autolog.vehicle.ui.design.motion.DirectionalContent
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.screenMargin
import kr.autolog.vehicle.ui.design.twoColumns
import kr.autolog.vehicle.ui.trips.tripWhenText

/** 넓은 창 왼쪽 섹션 목록이 차지하는 폭(콘텐츠 폭 대비). */
private const val MASTER_WIDTH = 0.4f

@Composable
fun SettingsRoute() {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    // 버전은 설치된 패키지에서(BuildConfig를 켜지 않는다), 데모 여부는 데모 패널이 있는 그래프인지로
    val app = remember(graph) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
        AppInfo(version, demo = graph.demoOverlay != null)
    }
    val vm = viewModel { SettingsViewModel(graph.settings, graph.navigator, graph.clock, app, graph.overlay) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, vm::onAction)
}

/**
 * 설정(스펙 7.9, 디자인 언어 7장 Master–Detail). 넓은 창: 왼쪽 섹션 목록 + 오른쪽 상세(처음엔 첫 섹션).
 * 좁은·세로 창: 섹션 목록 → 누르면 상세(머리 줄 제목이 섹션 이름, 뒤로 = 목록으로) — 경로는 하나, 안쪽 상태로 전환한다.
 * 팝업(연결 코드·추가·지우기)은 `SettingsDialogs.kt`, 섹션 내용은 `SettingsSections.kt`.
 */
@Composable
fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit, modifier: Modifier = Modifier) {
    // 넓은 창·분할 화면 한 칸은 목록|상세 두 칸, 세로로 긴 창만 목록 → 상세
    val wide = LocalWindowLayout.current.twoColumns
    val inDetail = !wide && state.section != null
    // 좁은 창에서 상세를 보고 있으면 시스템 뒤로도 목록으로(화면을 나가지 않게)
    BackHandler(enabled = inDetail) { onAction(SettingsAction.CloseSection) }
    Column(
        modifier.fillMaxSize().padding(screenMargin()),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        ScreenHeader(
            stringResource(state.section?.takeIf { inDetail }?.let(::sectionTitle) ?: R.string.settings_title),
        ) { onAction(if (inDetail) SettingsAction.CloseSection else SettingsAction.Back) }
        when {
            wide -> {
                val open = state.section ?: SettingsSection.entries.first()
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s7)) {
                    SectionList(state, onAction, Modifier.fillMaxWidth(MASTER_WIDTH).fillMaxHeight(), selected = open)
                    // 왼쪽에서 다른 섹션을 고르면 오른쪽 상세가 그 자리에서 교체된다
                    FadeThrough(open, Modifier.weight(1f).fillMaxHeight(), label = "settingsDetail") { section ->
                        ScrollColumn(Modifier.fillMaxSize()) { SectionDetail(section, state, onAction) }
                    }
                }
            }
            // 세로 창: 목록 → 상세는 앞으로(오른쪽에서), 상세 → 목록은 뒤로(왼쪽에서)
            else -> DirectionalContent(
                state.section,
                direction = { _, to -> if (to != null) 1 else -1 },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "settingsTall",
            ) { section ->
                if (section == null) {
                    SectionList(state, onAction, Modifier.fillMaxSize(), selected = null)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        ScrollColumn(Modifier.widthIn(max = Size.sheetMaxWidth).fillMaxWidth()) {
                            SectionDetail(section, state, onAction)
                        }
                    }
                }
            }
        }
    }
    SettingsDialogHost(state, onAction)
}

@Composable
private fun SectionList(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier,
    selected: SettingsSection?,
) {
    ScrollColumn(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            SettingsSection.entries.forEach { section ->
                NavRow(
                    icon = sectionIcon(section),
                    title = stringResource(sectionTitle(section)),
                    summary = sectionSummary(section, state),
                    onClick = { onAction(SettingsAction.Open(section)) },
                    selected = section == selected,
                )
            }
        }
    }
}

@StringRes
internal fun sectionTitle(section: SettingsSection): Int = when (section) {
    SettingsSection.VEHICLE -> R.string.settings_vehicle
    SettingsSection.SERVER -> R.string.settings_server
    SettingsSection.MAIL -> R.string.settings_mail
    SettingsSection.RECIPIENTS -> R.string.settings_recipients
    SettingsSection.STAFF -> R.string.settings_staff
    SettingsSection.AUTOMATION -> R.string.settings_automation
    SettingsSection.SYNC -> R.string.settings_sync
    SettingsSection.ABOUT -> R.string.settings_about
}

private fun sectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.VEHICLE -> Icons.Rounded.DirectionsCar
    SettingsSection.SERVER -> Icons.Rounded.CloudUpload
    SettingsSection.MAIL -> Icons.Rounded.Mail
    SettingsSection.RECIPIENTS -> Icons.Rounded.Group
    SettingsSection.STAFF -> Icons.Rounded.Badge
    SettingsSection.AUTOMATION -> Icons.Rounded.Event
    SettingsSection.SYNC -> Icons.Rounded.Sync
    SettingsSection.ABOUT -> Icons.Rounded.Info
}

/** 목록 줄 아래 지금 상태 한 줄 — 들어가 보지 않아도 무엇이 비었는지 알게. */
@Composable
private fun sectionSummary(section: SettingsSection, state: SettingsUiState): String = when (section) {
    SettingsSection.VEHICLE -> when (val l = state.link) {
        LinkUi.NotLinked -> stringResource(R.string.settings_not_linked)
        is LinkUi.Linked -> stringResource(R.string.settings_linked_vehicle, l.vehicle)
        is LinkUi.Unreachable -> stringResource(R.string.settings_unreachable_vehicle, l.vehicle)
    }
    SettingsSection.SERVER ->
        if (state.link == LinkUi.NotLinked) {
            stringResource(R.string.settings_not_linked)
        } else {
            stringResource(R.string.settings_upload_pending, state.upload.pending)
        }
    SettingsSection.MAIL ->
        stringResource(
            if (state.mailConnected) R.string.settings_mail_connected else R.string.settings_mail_not_connected,
        )
    SettingsSection.RECIPIENTS -> stringResource(R.string.settings_people_count, state.recipients.size)
    SettingsSection.STAFF -> stringResource(R.string.settings_people_count, state.staff.size)
    SettingsSection.AUTOMATION -> stringResource(
        if (state.autoMonthlySend) R.string.settings_on else R.string.settings_off,
    )
    SettingsSection.SYNC -> (state.link as? LinkUi.Linked)?.lastSync
        ?.let { stringResource(R.string.settings_last_sync, tripWhenText(it)) }
        ?: stringResource(R.string.settings_sync_summary)
    SettingsSection.ABOUT -> stringResource(R.string.settings_version, state.app.version)
}
