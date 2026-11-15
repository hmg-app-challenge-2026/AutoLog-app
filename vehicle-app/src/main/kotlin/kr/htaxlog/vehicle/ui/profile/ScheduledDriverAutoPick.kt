package kr.htaxlog.vehicle.ui.profile

import kotlinx.coroutines.flow.first
import kr.htaxlog.vehicle.data.repository.ProfileBadge
import kr.htaxlog.vehicle.data.repository.ProfileRepository
import kr.htaxlog.vehicle.data.repository.SessionRepository
import kr.htaxlog.vehicle.nav.Navigator
import kr.htaxlog.vehicle.nav.Route

/**
 * 앱이 켜질 때 한 번: 이 차·지금(±1시간) 배차된 운전자가 딱 한 명이고 아직 운전자가 없으면 그 사람으로 정한다(F-15, 2026-11-15).
 * 운전자 선택 화면이 떠 있든 아니든 동작한다 — 시동 직후에는 주행 여부를 아직 몰라(주행 단계) 내비게이터가 선택 화면을 Home으로
 * 바꿔 버리므로, 선택 화면 안에서만 고르면 실제로는 한 번도 실행되지 않았다. 배차가 없거나 둘 이상이면 아무것도 하지 않는다.
 */
class ScheduledDriverAutoPick(
    private val session: SessionRepository,
    private val profile: ProfileRepository,
    private val navigator: Navigator,
) {
    /** 고른 운전자 id. 고르지 않았으면 null. */
    suspend fun run(): String? {
        if (profile.currentDriver().first() != null) return null
        val scheduled = profile.orderedProfiles().first().filter { it.badge == ProfileBadge.SCHEDULED }
        val pick = scheduled.singleOrNull() ?: return null
        session.selectDriver(pick.userId)
        // 선택 화면이 떠 있으면 닫고 Home으로(이미 Home이면 그대로)
        if (navigator.current == Route.ProfilePicker) navigator.replaceAll(Route.Home)
        return pick.userId
    }
}
