package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kr.autolog.shared.PurposesPayload
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeInfo
import kr.autolog.vehicle.core.PurposeRouteRule
import java.util.UUID

/** [PurposeBook]이 통째로 저장하는 값. 목적은 PurposeDetail.name, 규칙은 [RuleData]. */
@Serializable
data class PurposeBookData(
    val serverPurposes: List<String> = emptyList(),
    val serverRules: List<RuleData> = emptyList(),
    val localPurposes: List<String> = emptyList(),
    val localRules: List<RuleData> = emptyList(),
    /** 이 차에서 뺀 회사 지정 목적(name)·규칙(ruleId). 서버가 다시 보내도 숨긴다. */
    val hiddenServer: Set<String> = emptySet(),
)

@Serializable
data class RuleData(val ruleId: String, val originPlaceId: String?, val destPlaceId: String, val purpose: String)

/** [PurposeBook] 저장소. Android 구현은 mail/PrefsPurposeBookStore(SharedPreferences JSON). */
interface PurposeBookStore {
    fun load(): PurposeBookData

    fun save(data: PurposeBookData)

    class InMemory(private var data: PurposeBookData = PurposeBookData()) : PurposeBookStore {
        override fun load() = data

        override fun save(data: PurposeBookData) {
            this.data = data
        }
    }
}

/**
 * 목적 목록과 출발→도착 목적 규칙 (F-08). 회사 지정(관리자 웹 → inbox PURPOSES)과 이 차에서 만든 것을 함께 든다.
 * - 회사 지정은 서버가 보낼 때마다 통째로 바뀐다. 이 차에서 뺀 것은 [PurposeBookData.hiddenServer]로 숨긴다.
 * - 규칙의 목적이 목록에 없더라도(회사가 목적을 지웠어도) 규칙은 이름 그대로 쓴다 — 목적 이름은 name 안에 있다.
 * 순수 코틀린(안드로이드 의존 없음)이라 JVM 테스트로 돈다.
 */
class PurposeBook(
    private val store: PurposeBookStore = PurposeBookStore.InMemory(),
    private val newId: () -> String = { "local-${UUID.randomUUID()}" },
) {
    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<PurposeBookData> = _data.asStateFlow()

    /** 기본 목적(미확정 제외) → 회사 지정 → 이 차 순. 같은 이름은 앞의 것 하나만. */
    fun purposes(d: PurposeBookData = _data.value): List<PurposeInfo> {
        val builtIn = PurposeDetail.entries.filter { it.category != PurposeCategory.UNDETERMINED }
            .map { PurposeInfo(it, null) }
        val server = d.serverPurposes.filter { it !in d.hiddenServer }
            .mapNotNull { PurposeDetail.fromName(it) }
            .map { PurposeInfo(it, Origin.SERVER) }
        val local = d.localPurposes.mapNotNull { PurposeDetail.fromName(it) }.map { PurposeInfo(it, Origin.LOCAL) }
        return (builtIn + server + local).distinctBy { it.detail.name }
    }

    /** 추천에 쓸 규칙(회사 지정 먼저, 숨긴 것 제외). */
    fun rules(d: PurposeBookData = _data.value): List<PurposeRouteRule> =
        d.serverRules.filter { it.ruleId !in d.hiddenServer }.mapNotNull { it.toRule(Origin.SERVER) } +
            d.localRules.mapNotNull { it.toRule(Origin.LOCAL) }

    /** 서버 편지 반영. 숨긴 목록은 지금 서버에 있는 것만 남긴다(회사가 지웠다가 다시 만든 것은 다시 보인다). */
    fun applyServer(p: PurposesPayload) = update { d ->
        val purposes = p.purposes.mapNotNull { dto ->
            val category = PurposeCategory.entries.firstOrNull { it.name == dto.category } ?: return@mapNotNull null
            runCatching { PurposeDetail.custom(dto.label, category).name }.getOrNull()
        }
        val rules = p.rules.map { RuleData(it.ruleId, it.originPlaceId, it.destPlaceId, it.purpose) }
        val alive = purposes.toSet() + rules.map { it.ruleId }
        d.copy(serverPurposes = purposes, serverRules = rules, hiddenServer = d.hiddenServer.intersect(alive))
    }

    fun addPurpose(label: String, category: PurposeCategory): Result<PurposeDetail> {
        val detail = runCatching { PurposeDetail.custom(label, category) }.getOrElse {
            return Result.failure(IllegalArgumentException("목적 이름을 ${PurposeDetail.MAX_LABEL}자 안으로 입력해 주세요"))
        }
        val d = _data.value
        if (detail.name in d.hiddenServer) {
            update { it.copy(hiddenServer = it.hiddenServer - detail.name) } // 뺐던 회사 지정을 다시 보인다
            return Result.success(detail)
        }
        if (purposes(d).any { it.detail.labelKo == detail.labelKo }) {
            return Result.failure(IllegalArgumentException("이미 있는 목적입니다"))
        }
        update { it.copy(localPurposes = it.localPurposes + detail.name) }
        return Result.success(detail)
    }

    fun removePurpose(name: String): Result<Unit> {
        val d = _data.value
        return when {
            PurposeDetail.entries.any { it.name == name } -> Result.failure(IllegalArgumentException("기본 목적은 뺄 수 없습니다"))
            name in d.localPurposes -> {
                update {
                    it.copy(
                        localPurposes = it.localPurposes - name,
                        localRules = it.localRules.filter { r ->
                            r.purpose !=
                                name
                        },
                    )
                }
                Result.success(Unit)
            }
            name in d.serverPurposes -> {
                update {
                    it.copy(
                        hiddenServer = it.hiddenServer + name,
                        localRules = it.localRules.filter { r ->
                            r.purpose !=
                                name
                        },
                    )
                }
                Result.success(Unit)
            }
            else -> Result.failure(IllegalArgumentException("없는 목적입니다"))
        }
    }

    fun addRule(originPlaceId: String?, destPlaceId: String, purposeName: String): Result<String> {
        val detail = PurposeDetail.fromName(purposeName)
            ?: return Result.failure(IllegalArgumentException("목적을 골라 주세요"))
        if (detail.category == PurposeCategory.UNDETERMINED) {
            return Result.failure(IllegalArgumentException("목적을 골라 주세요"))
        }
        if (originPlaceId == destPlaceId) return Result.failure(IllegalArgumentException("출발과 도착이 같은 장소입니다"))
        if (rules().any { it.originPlaceId == originPlaceId && it.destPlaceId == destPlaceId }) {
            return Result.failure(IllegalArgumentException("같은 출발·도착 규칙이 이미 있습니다"))
        }
        val id = newId()
        update { it.copy(localRules = it.localRules + RuleData(id, originPlaceId, destPlaceId, detail.name)) }
        return Result.success(id)
    }

    fun removeRule(ruleId: String): Result<Unit> {
        val d = _data.value
        return when {
            d.localRules.any { it.ruleId == ruleId } -> {
                update { it.copy(localRules = it.localRules.filter { r -> r.ruleId != ruleId }) }
                Result.success(Unit)
            }
            d.serverRules.any { it.ruleId == ruleId } -> {
                update { it.copy(hiddenServer = it.hiddenServer + ruleId) }
                Result.success(Unit)
            }
            else -> Result.failure(IllegalArgumentException("없는 규칙입니다"))
        }
    }

    private fun update(f: (PurposeBookData) -> PurposeBookData) {
        synchronized(this) {
            val next = f(_data.value)
            store.save(next)
            _data.value = next
        }
    }

    private fun RuleData.toRule(origin: Origin): PurposeRouteRule? =
        PurposeDetail.fromName(purpose)?.let { PurposeRouteRule(ruleId, originPlaceId, destPlaceId, it, origin) }
}
