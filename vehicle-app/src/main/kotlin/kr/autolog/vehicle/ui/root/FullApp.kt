package kr.autolog.vehicle.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.di.AppGraph
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalLeaving
import kr.autolog.vehicle.ui.design.LocalNavAnimatedScope
import kr.autolog.vehicle.ui.design.LocalSharedTransitionScope
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.behindDimTarget
import kr.autolog.vehicle.ui.design.motion.navTransitionOf
import kr.autolog.vehicle.ui.design.motion.routeTransform
import kr.autolog.vehicle.ui.history.HistoryRoute
import kr.autolog.vehicle.ui.home.HomeRoute
import kr.autolog.vehicle.ui.home.isIdle
import kr.autolog.vehicle.ui.profile.ProfilePickerRoute
import kr.autolog.vehicle.ui.receipt.FuelEntryRoute
import kr.autolog.vehicle.ui.receipt.ReceiptQrRoute
import kr.autolog.vehicle.ui.report.ReportRoute
import kr.autolog.vehicle.ui.result.TripResultRoute
import kr.autolog.vehicle.ui.settings.SettingsRoute

/**
 * 전체 앱(주차·정차·주행 모두 — D-31): 백스택 맨 위 화면. 정차·주행 중엔 Navigator가 Home만 남긴다. 전환 모양은 스택 변화가 정한다([navTransitionOf] — 앞으로·뒤로·띄우기·내리기·같은 자리, 디자인 언어 8.2).
 * 프로필 → 오른쪽 위 배지는 hero. 화면마다 불투명 바탕을 깔아 겹쳐 움직일 때 뒤 화면 글자가 비치지 않게 한다.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)
@Composable
fun FullApp(graph: AppGraph) {
    val stack by graph.navigator.backStack.collectAsStateWithLifecycle()
    // 운전자 배지는 Home이 "대기 중"일 때만 — 모르는 동안(첫 값 전)은 숨긴다
    val session by graph.session.state.collectAsStateWithLifecycle(initialValue = null)
    // 정차·주행 중엔 감춘다 — 운전자는 주차한 대기 Home에서만 보고 바꾼다(D-30·D-31)
    val idle = session?.isIdle() == true && !LocalDrivingLocked.current
    val motion = AutoLogTheme.motion
    val c = AutoLogTheme.colors
    val density = LocalDensity.current
    val risePx = with(density) { Space.s9.roundToPx() }
    val dropPx = with(density) { Space.s7.roundToPx() }
    val stores: RouteViewModelStores = viewModel()
    val transition = updateTransition(stack, label = "route")
    // 스택에서 빠진 화면의 ViewModel은 전환이 **끝난 뒤** 지운다 — 뒤로 가는 동안 떠나는 화면이 아직 그려지고 있다
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (transition.currentState == transition.targetState) stores.retainOnly(transition.currentState)
    }
    BackHandler(enabled = stack.size > 1) { graph.navigator.pop() }
    SharedTransitionLayout {
        transition.AnimatedContent(
            transitionSpec = {
                routeTransform(navTransitionOf(initialState, targetState), motion, risePx, dropPx)
            },
            contentKey = { it.last() },
        ) { shown ->
            val route = shown.last()
            val kind = navTransitionOf(transition.currentState, transition.targetState)
            val dim by this.transition.animateFloat(transitionSpec = { motion.smooth() }, label = "behindDim") {
                behindDimTarget(kind, it)
            }
            val leaving = this.transition.targetState == EnterExitState.PostExit
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalNavAnimatedScope provides this,
                // 화면마다 자기 ViewModel 저장소 — 화면의 viewModel {}은 이 화면이 스택에 있는 동안만 산다
                LocalViewModelStoreOwner provides remember(route) { stores.ownerFor(route) },
                // 나가는 화면은 누를 수 없다(전환 중 두 번 눌러 화면이 두 번 열리지 않게). 새 화면은 첫 프레임부터 누를 수 있다
                LocalLeaving provides leaving,
            ) {
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    if (route == Route.Home) {
                        // 오른쪽 위 운전자 배지 → 탭하면 운전자 선택. 기록 중에는 감춘다(틀은 그대로라 Home 상태가 바뀌어도 화면을 다시 만들지 않는다)
                        DriverBadgeScaffold(visible = idle, onClick = { graph.navigator.push(Route.ProfilePicker) }) {
                            RouteContent(route)
                        }
                    } else {
                        RouteContent(route)
                    }
                    // 앞으로 갈 때 밀려나는 화면 · 뒤로 올 때 돌아오는 화면 위 덮개(iOS처럼 깊이가 읽힌다)
                    if (dim > 0f) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                alpha = dim * motion.pushBehindDim
                            }.background(c.scrim),
                        )
                    }
                }
            }
        }
    }
}

// 오른쪽 위 운전자 배지(디자인 언어 11장 DriverBadge)는 **Home "대기 중" 하나뿐**(D-30, 2026-11-10 사용자 결정):
// 운전자는 운행을 시작하기 전 메인 화면에서 보고 바꾸는 것이고, 기록 중 Home·정차 중 화면·보조 화면은 그 자리를 내용에 쓴다.

@Composable
private fun RouteContent(route: Route) {
    when (route) {
        Route.ProfilePicker -> ProfilePickerRoute()
        Route.Home -> HomeRoute()
        is Route.TripResult -> TripResultRoute(route.tripId, route.mode)
        Route.ReceiptQr -> ReceiptQrRoute()
        is Route.FuelEntry -> FuelEntryRoute(route.receiptId)
        Route.Report -> ReportRoute()
        Route.History -> HistoryRoute()
        Route.Settings -> SettingsRoute()
    }
}
