package kr.htaxlog.vehicle.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.htaxlog.vehicle.core.VehicleMotion
import kr.htaxlog.vehicle.data.usage.UsageTier
import kr.htaxlog.vehicle.di.AppGraph
import kr.htaxlog.vehicle.di.LocalAppGraph
import kr.htaxlog.vehicle.ui.design.HTaxLogTheme
import kr.htaxlog.vehicle.ui.design.LocalDrivingLocked
import kr.htaxlog.vehicle.ui.design.LocalInputLocked
import kr.htaxlog.vehicle.ui.design.LocalKeyboardLocked
import kr.htaxlog.vehicle.ui.design.LocalVehicleMoving
import kr.htaxlog.vehicle.ui.design.WindowLayoutHost

/** FULL 진입 직후 오조작 방지(디자인 언어 12장). */
const val FULL_ENTRY_GUARD_MS = 300L

/**
 * 스펙 6.9 2번·D-31: **화면은 하나**(전체 앱) — 주차·정차·주행이 같은 Home·같은 배치를 그린다. 단계는 잠금만 바꾼다:
 * 주차(FULL) = 전부 · 정차(PEEK) = 읽기 전용(타일은 흐리게, 첫 칸 [기록 시작]·[기록 종료]만 눌림, D-36) · 주행(BACKGROUND) = 같은 읽기 전용 화면 + 돌아갈 앱이 있으면 뒤로 물러남.
 * 내려가면 Navigator가 Home만 남긴다(운행 직후 결과 카드는 버튼 없이 남는다, D-35). 입력 잠금 = tier != FULL 또는 FULL 진입 직후 300 ms.
 * 예전엔 정차 때 별도 패널(Peek), 주행 때 빈 화면으로 통째로 바꿔 그려 단계가 바뀔 때마다 다른 앱처럼 보였다(2026-11-11 사용자 지시로 하나로).
 */
@Composable
fun HTaxLogRoot(graph: AppGraph, onRequestBackground: () -> Unit, deferBackgroundUntilMotionKnown: Boolean = false) {
    val tier by graph.usage.tier.collectAsStateWithLifecycle()
    val motion by graph.usage.motion.collectAsStateWithLifecycle()
    val noKeyboard by graph.usage.noKeyboard.collectAsStateWithLifecycle()
    var entryGuard by remember { mutableStateOf(false) }
    var bootOpening by remember { mutableStateOf(deferBackgroundUntilMotionKnown) }
    LaunchedEffect(tier, motion, deferBackgroundUntilMotionKnown) {
        graph.navigator.onTierChanged(tier)
        when (tier) {
            UsageTier.BACKGROUND -> {
                // 판정 전(null)에도 입력은 잠그지만, 실제 주행을 확인하기 전에는 앱 창을 내리지 않는다.
                if (motion == VehicleMotion.MOVING && bootOpening) {
                    // 시동 직후 화면 실행을 택했다면 일시적인 주행 신호에 화면이 바로 사라지지 않게 한다.
                    delay(3_000)
                    if (graph.usage.motion.value == VehicleMotion.MOVING) onRequestBackground()
                    bootOpening = false
                } else if (motion == VehicleMotion.MOVING) {
                    onRequestBackground()
                }
            }
            UsageTier.FULL -> {
                bootOpening = false
                entryGuard = true
                delay(FULL_ENTRY_GUARD_MS)
                entryGuard = false
            }
            UsageTier.PEEK -> {
                bootOpening = false
            }
        }
    }
    val driving = tier != UsageTier.FULL
    CompositionLocalProvider(LocalAppGraph provides graph) {
        HTaxLogTheme(staticMotion = driving) {
            CompositionLocalProvider(
                LocalInputLocked provides (driving || entryGuard),
                LocalDrivingLocked provides driving,
                LocalVehicleMoving provides (tier == UsageTier.BACKGROUND),
                LocalKeyboardLocked provides noKeyboard,
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        HTaxLogTheme.colors.bg,
                    ).windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    WindowLayoutHost { FullApp(graph) }
                    graph.demoOverlay?.invoke()
                }
            }
        }
    }
}
