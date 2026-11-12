package kr.autolog.vehicle.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.DriverSide
import kr.autolog.vehicle.ui.design.LocalDriverSide
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.DockTile

/**
 * 타일 하나. [emphasized] = 같은 자리에서 상태가 바뀐 주 동작(파란 채움).
 * [whileDriving] = 정차·주행 중에도 누를 수 있다 — 첫 칸 [기록 종료](D-31)·[기록 시작](D-36)만. 나머지는 흐리게 잠긴다.
 */
private data class DockItem(
    val icon: ImageVector,
    @StringRes val label: Int,
    val action: HomeAction,
    val emphasized: Boolean = false,
    val whileDriving: Boolean = false,
)

private val FUEL = DockItem(Icons.Rounded.LocalGasStation, R.string.action_fuel_add, HomeAction.AddFuelRecord)
private val RECORDS = DockItem(Icons.Rounded.Description, R.string.action_records, HomeAction.OpenRecords)
private val SETTINGS = DockItem(Icons.Rounded.Settings, R.string.action_settings, HomeAction.OpenSettings)

// 정차·주행 중에도 눌린다(D-36) — 사람이 끝내면 다음 주차까지 자동 시작이 멈추므로(D-34), 잘못 끝냈을 때 되돌릴 길
private val START =
    DockItem(Icons.Rounded.PlayArrow, R.string.home_start_manual, HomeAction.StartTrip, whileDriving = true)
private val END =
    DockItem(Icons.Rounded.Stop, R.string.home_end, HomeAction.EndNow, emphasized = true, whileDriving = true)

/**
 * 맨 아래 타일 4개(D-24): [기록 시작] · [주유 기록 추가] · [운행 기록 조회] · [설정].
 * 모두 같은 [DockTile] — 크기·모양·간격이 같고 아이콘만 다르다. 기록 중에는 첫 칸이 같은 자리에서 [기록 종료](파란 채움).
 * 기록 시작·종료가 주 동작이라 운전석 쪽 끝(디자인 언어 6.3). 언제나 4칸 한 줄, 순서는 `driverSide`(우핸들이면 좌우 반전).
 * 정차·주행 중에도 같은 자리에 그대로 있고 첫 칸([기록 시작]·[기록 종료])만 눌린다(D-31·D-36).
 */
@Composable
internal fun HomeDock(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    if (state.phase == HomePhase.NEEDS_RECOVERY) return
    val record = if (state.phase == HomePhase.READY) START else END
    val ordered = listOf(record, FUEL, RECORDS, SETTINGS)
    val items = if (LocalDriverSide.current == DriverSide.START) ordered else ordered.reversed()
    // 4칸 등분 — 네모가 같은 간격으로 놓인다. 좁은 창에서도 한 줄(이름은 필요하면 두 줄)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
        items.forEach { item ->
            DockTile(
                item.icon,
                stringResource(item.label),
                onClick = { onAction(item.action) },
                modifier = Modifier.weight(1f),
                emphasized = item.emphasized,
                whileDriving = item.whileDriving,
            )
        }
    }
}
