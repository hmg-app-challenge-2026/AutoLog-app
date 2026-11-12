package kr.autolog.vehicle.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.autolog.vehicle.data.usage.UsageTier

/** 스펙 D-06·6.10. 단계 규칙을 한 곳에서 처리한다. */
class Navigator(start: Route = Route.ProfilePicker) {
    private val _backStack = MutableStateFlow(listOf(start))
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()
    private var tier = UsageTier.FULL

    /** 첫 [onTierChanged] 전에는 사용 단계를 아직 모른다(앱 시작 직후). */
    private var tierKnown = false

    /** 주차가 아닐 때 들어온 딥링크 — 다음에 주차(FULL)가 되면 연다. */
    private var pendingDeepLink: Route? = null

    /** 주차가 아닐 때 열려던 운행 결과 — 다음에 주차가 되면 연다([showWhenParked]). */
    private var pendingParked: Route? = null

    val current: Route get() = _backStack.value.last()

    fun push(route: Route): Boolean {
        if (tier != UsageTier.FULL) return false
        _backStack.value = _backStack.value + route
        return true
    }

    /** QR에서 영수증 입력으로 바뀔 때 맨 위 화면만 교체한다. */
    fun replaceTop(route: Route): Boolean {
        if (tier != UsageTier.FULL) return false
        _backStack.value = _backStack.value.dropLast(1) + route
        return true
    }

    fun replaceAll(route: Route): Boolean {
        if (tier != UsageTier.FULL) return false
        _backStack.value = listOf(route)
        return true
    }

    fun pop(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.value = _backStack.value.dropLast(1)
        return true
    }

    /**
     * 알림 탭(스펙 6.10). 주차면 Home 위에 [route]를 바로 연다(뒤로 가면 Home).
     * 주행·정차 중이거나 아직 단계를 모르면 기억해 두었다가 **다음 주차 때** 연다 — 운전 중에 화면을 띄우지 않는다. 새 딥링크가 오면 이전 것을 대신한다.
     */
    fun handleDeepLink(route: Route) {
        if (tierKnown && tier == UsageTier.FULL) {
            _backStack.value = listOf(Route.Home, route)
        } else {
            pendingDeepLink = route
        }
    }

    /**
     * "지금 주차면 바로, 아니면 다음 주차 때" 열 화면(운행 결과 — 정차 중 [기록 종료]·자동 종료로 끝난 운행, D-31).
     * 주차면 맨 위에 쌓는다(이미 맨 위면 그대로). [route]가 null이면 기다리던 것을 지운다(그 운행이 다시 기록 중이 됐다 등).
     */
    fun showWhenParked(route: Route?) {
        pendingParked = null
        if (route == null) return
        if (tier == UsageTier.FULL) {
            if (current != route) push(route)
        } else {
            pendingParked = route
        }
    }

    /**
     * FULL에서 내려가면 [Home]만 남긴다(스펙 6.9 3번). 올라올 때는 화면을 되살리지 않는다 —
     * 단, 그사이 들어온 딥링크(사용자가 알림을 눌러 요청한 화면)는 연다. 딥링크가 없으면 기다리던 운행 결과([showWhenParked])를 연다.
     */
    fun onTierChanged(newTier: UsageTier) {
        if (newTier != UsageTier.FULL && (tier == UsageTier.FULL || _backStack.value != listOf(Route.Home))) {
            _backStack.value = listOf(Route.Home)
        }
        tier = newTier
        tierKnown = true
        if (newTier == UsageTier.FULL) {
            (pendingDeepLink ?: pendingParked)?.let { _backStack.value = listOf(Route.Home, it) }
            pendingDeepLink = null
            pendingParked = null
        }
    }
}
