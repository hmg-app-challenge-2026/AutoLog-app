package kr.autolog.vehicle.demo

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.SecondaryButton

/** 왼쪽 위 96 dp 영역(오른쪽 위는 운전자 배지) 길게 누르기 → 패널. 데모 전용이라 잠금을 무시한다(정차 중에도 주차로 되돌릴 수 있게). */
@Composable
fun DemoPanelOverlay(scenario: DemoScenario) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Box(Modifier.size(Size.iconButton).pointerInput(Unit) { detectTapGestures(onLongPress = { open = true }) })
    }
    if (open) {
        CompositionLocalProvider(LocalInputLocked provides false) {
            ModalCard(maxWidth = Size.sheetMaxWidth, title = stringResource(R.string.demo_title), onClose = {
                open =
                    false
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    val item =
                        @Composable { label: Int, action: () -> Unit ->
                            SecondaryButton(
                                stringResource(label),
                                {
                                    action()
                                    open = false
                                },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    item(R.string.demo_play_drive) { scenario.playDrive() }
                    item(R.string.demo_stop) { scenario.stop() }
                    item(R.string.demo_park) { scenario.park() }
                    item(R.string.demo_add_km) { scenario.addKm() }
                    item(R.string.demo_missed) { scenario.missedTrip() }
                    item(R.string.demo_recovery) { scenario.recovery() }
                }
            }
        }
    }
}
