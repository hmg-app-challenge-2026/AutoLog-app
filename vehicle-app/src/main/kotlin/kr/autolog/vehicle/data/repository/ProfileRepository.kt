package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.PurposeEngine
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.UserBadge

enum class ProfileBadge { SCHEDULED, RECENT }

data class DriverProfile(val userId: String, val name: String, val dept: String?, val badge: ProfileBadge? = null)

sealed interface AddDriverResult {
    data class Added(val userId: String) : AddDriverResult

    /** 화면이 문구를 고를 수 있게 실패 종류를 타입으로 나눈다(문구는 strings.xml). */
    sealed interface Failure : AddDriverResult

    data object EmptyName : Failure

    /** 이름과 부서가 모두 같은 직원이 이미 있다. 부서가 다른 동명이인은 추가할 수 있다. */
    data object Duplicate : Failure

    /** 원천(SettingsApi)이 거절 — 사유 문구는 원천이 준다. */
    data class Rejected(val reason: String) : Failure
}

interface ProfileRepository {
    /** 추천 순서(배차 → 최근 → 이름순) + 추천 엔진이 아직 모르는 직원(방금 추가한 사람 등)은 뒤에 이름순. */
    fun orderedProfiles(): Flow<List<DriverProfile>>

    fun currentDriver(): Flow<DriverProfile?>

    /** 차 안에서 새 운전자 추가(origin=LOCAL, F-24). 추가된 사람의 id를 돌려준다. */
    suspend fun addDriver(name: String, dept: String?): AddDriverResult
}

class DefaultProfileRepository(
    private val purpose: PurposeEngine,
    private val settings: SettingsApi,
    private val currentDriverId: StateFlow<String?>,
    private val vehicleId: () -> String,
    private val clock: AppClock,
) : ProfileRepository {
    override fun orderedProfiles(): Flow<List<DriverProfile>> =
        combine(purpose.orderedUsers(vehicleId(), clock.nowMs()), settings.users()) { cards, users ->
            val ordered =
                cards.map { card ->
                    DriverProfile(
                        userId = card.userId,
                        name = card.name,
                        dept = card.dept,
                        badge =
                        when (card.badge) {
                            UserBadge.SCHEDULED -> ProfileBadge.SCHEDULED
                            UserBadge.RECENT -> ProfileBadge.RECENT
                            null -> null
                        },
                    )
                }
            val known = ordered.map { it.userId }.toSet()
            ordered +
                users.filter {
                    it.userId !in known
                }.sortedBy { it.name }.map { DriverProfile(it.userId, it.name, it.dept) }
        }

    override fun currentDriver(): Flow<DriverProfile?> = combine(currentDriverId, settings.users()) { id, users ->
        users.firstOrNull { it.userId == id }?.let { DriverProfile(it.userId, it.name, it.dept) }
    }.distinctUntilChanged()

    override suspend fun addDriver(name: String, dept: String?): AddDriverResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddDriverResult.EmptyName
        val cleanDept = dept?.trim()?.ifEmpty { null }
        val existing = settings.users().first()
        if (existing.any { it.name == trimmed && it.dept == cleanDept }) return AddDriverResult.Duplicate
        // 동명이인(부서가 다름)이 있을 수 있으므로 이름이 아니라 "추가 전에 없던 id"로 새 사람을 찾는다
        val before = existing.mapTo(HashSet()) { it.userId }
        return when (val r = settings.addUser(trimmed, cleanDept, null)) {
            is SettingsResult.Invalid -> AddDriverResult.Rejected(r.reason)
            SettingsResult.Ok ->
                settings.users().first().firstOrNull { it.userId !in before }?.let { AddDriverResult.Added(it.userId) }
                    ?: AddDriverResult.Rejected("added user not found")
        }
    }
}
