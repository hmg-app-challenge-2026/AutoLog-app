package kr.autolog.vehicle.domain

import kr.autolog.vehicle.core.RecentUse
import kr.autolog.vehicle.core.UserBadge
import kr.autolog.vehicle.core.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class UserOrderingTest {
    private val users =
        listOf(
            UserInfo("u-han", "한도윤"),
            UserInfo("u-kim", "김영업"),
            UserInfo("u-lee", "이현장"),
            UserInfo("u-park", "박관리"),
            UserInfo("u-choi", "최회계"),
        )

    @Test
    fun `배차 운전자가 첫 카드이고 배차 배지가 붙는다`() {
        val cards = UserOrdering.order(users, listOf("u-lee"), previousDriverId = "u-kim", recent = emptyList())

        assertEquals("u-lee", cards[0].userId)
        assertEquals(UserBadge.SCHEDULED, cards[0].badge)
        assertEquals("u-kim", cards[1].userId)
    }

    @Test
    fun `배차가 없으면 직전 운전자, 그다음 최근 사용 순`() {
        val recent = listOf(RecentUse("u-park", 100), RecentUse("u-choi", 300))

        val ids = UserOrdering.order(users, emptyList(), "u-kim", recent).map { it.userId }

        assertEquals(listOf("u-kim", "u-choi", "u-park", "u-lee", "u-han"), ids)
    }

    @Test
    fun `나머지는 가나다순이고 배지가 없다`() {
        val cards = UserOrdering.order(users, emptyList(), null, emptyList())

        assertEquals(listOf("김영업", "박관리", "이현장", "최회계", "한도윤"), cards.map { it.name })
        assertEquals(setOf(null), cards.map { it.badge }.toSet())
    }

    @Test
    fun `최근 배지는 가장 최근 1명만`() {
        val recent = (1..5).map { RecentUse(users[it - 1].userId, it.toLong()) }

        val cards = UserOrdering.order(users, emptyList(), null, recent)

        assertEquals(listOf(users[4].userId), cards.filter { it.badge == UserBadge.RECENT }.map { it.userId })
    }

    @Test
    fun `직원 목록에 없는 배차 운전자는 건너뛴다`() {
        val cards = UserOrdering.order(users, listOf("u-unknown"), null, emptyList())

        assertEquals(5, cards.size)
    }
}
