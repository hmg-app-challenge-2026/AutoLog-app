package kr.autolog.vehicle.domain

import kr.autolog.vehicle.core.RecentUse
import kr.autolog.vehicle.core.UserBadge
import kr.autolog.vehicle.core.UserCard
import kr.autolog.vehicle.core.UserInfo
import java.text.Collator
import java.util.Locale

/**
 * 운전자 선택 화면의 카드 순서 (F-15, 기술명세 2.13).
 * ① 이 차·지금 ±1시간 배차 운전자 ② 직전 운행 운전자 ③ 최근 사용 순 ④ 나머지 이름순.
 * 순수 함수라 오프라인에서도 같은 결과가 나온다.
 */
object UserOrdering {
    const val SCHEDULE_WINDOW_MS = 60 * 60 * 1000L

    /** 최근 배지를 붙이는 사람 수 — 가장 최근 1명만(2026-11-15 사용자 결정, 예전 3명). */
    const val RECENT_BADGE_COUNT = 1

    private val nameOrder: Comparator<UserInfo> =
        Collator.getInstance(Locale.KOREAN).let { c -> Comparator { a, b -> c.compare(a.name, b.name) } }

    fun order(
        users: List<UserInfo>,
        scheduledUserIds: List<String>,
        previousDriverId: String?,
        recent: List<RecentUse>,
    ): List<UserCard> {
        val byId = users.associateBy { it.userId }
        val recentIds = recent.sortedByDescending { it.lastUsedAt }.map { it.userId }
        val recentBadge = (listOfNotNull(previousDriverId) + recentIds).distinct().take(RECENT_BADGE_COUNT).toSet()

        val head = (scheduledUserIds + listOfNotNull(previousDriverId) + recentIds).distinct().mapNotNull { byId[it] }
        val headIds = head.map { it.userId }.toSet()
        val rest = users.filterNot { it.userId in headIds }.sortedWith(nameOrder)

        return (head + rest).map { u ->
            val badge =
                when (u.userId) {
                    in scheduledUserIds -> UserBadge.SCHEDULED
                    in recentBadge -> UserBadge.RECENT
                    else -> null
                }
            UserCard(u.userId, u.name, u.dept, badge)
        }
    }
}
