package kr.autolog.vehicle.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.ProfileBadge
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ActionTile
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.ProfileTile
import kr.autolog.vehicle.ui.design.components.TertiaryButton
import kr.autolog.vehicle.ui.design.twoColumns

@Composable
fun ProfilePickerRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { ProfilePickerViewModel(graph.session, graph.profile, graph.navigator) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    ProfilePickerScreen(state, vm::onAction)
}

/**
 * 스펙 7.1. 제목과 타일이 화면 가운데 주인공이다(넷플릭스식). 타일 순서는 [ProfilePickerUiState.tiles]가 정한다.
 * WIDE·NARROW(분할 한 칸) = 한 줄, TALL = 3열.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePickerScreen(
    state: ProfilePickerUiState,
    onAction: (ProfilePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val layout = LocalWindowLayout.current
    // 넓은 창·분할 화면 한 칸 200 dp·한 줄 / 세로로 긴 창(TALL) 160 dp·3열
    val tile = if (layout.twoColumns) Size.profileTileWide else Size.profileTileNarrow
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s6, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.picker_title),
                style = AutoLogTheme.windowType.screenTitle,
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (state.loaded && state.profiles.isEmpty()) {
                Text(stringResource(R.string.picker_empty), style = t.body, color = c.textSecondary)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s6, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Space.s5),
                maxItemsInEachRow = if (layout.twoColumns) Int.MAX_VALUE else 3,
            ) {
                state.tiles.forEach { PickerTileOf(it, tile, onAction) }
            }
            TertiaryButton(stringResource(R.string.picker_later), onClick = { onAction(ProfilePickerAction.Later) })
        }
        ModalHost(visible = state.showAll) { AllProfilesDialog(state.profiles, onAction) }
        ModalHost(state.addForm) { AddDriverDialog(it, onAction) }
    }
}

@Composable
private fun PickerTileOf(tile: PickerTile, size: Dp, onAction: (ProfilePickerAction) -> Unit) = when (tile) {
    is PickerTile.Person -> ProfileTileOf(tile.profile, size) {
        onAction(ProfilePickerAction.Select(tile.profile.userId))
    }
    PickerTile.More ->
        ActionTile(Icons.Rounded.MoreHoriz, stringResource(R.string.picker_more), onClick = {
            onAction(ProfilePickerAction.ShowAll)
        }, tileSize = size)
    PickerTile.Add ->
        ActionTile(Icons.Rounded.Add, stringResource(R.string.picker_add), onClick = {
            onAction(ProfilePickerAction.OpenAdd)
        }, tileSize = size)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllProfilesDialog(profiles: List<DriverProfile>, onAction: (ProfilePickerAction) -> Unit) {
    // 제목 없음: "다른 사람"을 눌러 펼친 목록이라 무엇을 고르는지 이미 안다 — 같은 정보를 두 번 주지 않는다.
    // 고르기만 하는 팝업이라 하단 [취소] 대신 오른쪽 위 X로 닫는다
    ModalCard(maxWidth = Size.sheetMaxWidth, onClose = { onAction(ProfilePickerAction.HideAll) }) {
        // 카드 하나의 스크롤로 흐른다 — 높이 고정 격자를 카드 안에 넣으면 스크롤이 두 겹이 되고 줄이 잘렸다
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s5, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.s5),
        ) {
            profiles.forEach { p ->
                // 뒤 화면의 같은 사람 타일과 shared element 키가 겹치지 않게 팝업 쪽은 끈다
                ProfileTileOf(p, Size.profileTileNarrow, sharedKey = null) {
                    onAction(ProfilePickerAction.Select(p.userId))
                }
            }
        }
    }
}

@Composable
private fun ProfileTileOf(p: DriverProfile, size: Dp, sharedKey: String? = p.userId, onClick: () -> Unit) = ProfileTile(
    userId = p.userId,
    name = p.name,
    dept = p.dept,
    scheduled = p.badge == ProfileBadge.SCHEDULED,
    recent = p.badge == ProfileBadge.RECENT,
    onClick = onClick,
    tileSize = size,
    sharedKey = sharedKey,
)
