package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PlaceAddResult
import kr.autolog.vehicle.core.PlaceCandidate
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.PlaceSearchResult
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeInfo
import kr.autolog.vehicle.core.PurposeRuleInfo
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.RelayClient
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo
import java.text.Collator
import java.util.Locale
import java.util.UUID

/** 월말 자동 발송 스위치 저장소. Android 구현은 mail/PrefsAutoSendPreference. */
interface AutoSendPreference {
    fun load(): Boolean

    fun save(enabled: Boolean)
}

/**
 * 이 차에서 뺀 회사 지정(SERVER) 받는 사람의 이메일(소문자). 서버가 목록을 다시 보내도 이 차에서는 계속 숨긴다.
 * Android 구현은 mail/PrefsHiddenRecipients.
 */
interface HiddenRecipientStore {
    fun load(): Set<String>

    fun save(emails: Set<String>)

    /** 저장하지 않는 구현(테스트·가짜용). */
    class InMemory(private var emails: Set<String> = emptySet()) : HiddenRecipientStore {
        override fun load() = emails

        override fun save(emails: Set<String>) {
            this.emails = emails
        }
    }
}

/**
 * [SettingsApi] 구현 (F-24). 수신자·직원은 차 안 Room([DomainStore])에 origin=LOCAL로 저장한다.
 * 서버 배포분(origin=SERVER)은 inbox 수신 때 바뀌므로 Room에서 지우지 않고, 이 차에서 빼면 [hidden]에 적어 목록에서 숨긴다.
 */
class SettingsService(
    private val store: DomainStore,
    private val relay: RelayClient,
    private val autoSend: AutoSendPreference,
    private val hidden: HiddenRecipientStore = HiddenRecipientStore.InMemory(),
    private val purposeBook: PurposeBook = PurposeBook(),
    /** 장소 검색(중계 서버 `/v1/geo/search`). 실제 앱은 AutoLog가 잇는다. */
    private val search: suspend (String) -> PlaceSearchResult = { PlaceSearchResult.Failed("서버에 연결되지 않았어요") },
    /** 지금 차 위치 + 주소. */
    private val here: suspend () -> PlaceCandidate? = { null },
    private val newId: () -> String = { "local-${UUID.randomUUID()}" },
    private val log: (String) -> Unit = {},
) : SettingsApi {
    private val autoState = MutableStateFlow(autoSend.load())

    private val hiddenState = MutableStateFlow(hidden.load())

    override fun recipients(): Flow<List<RecipientInfo>> =
        combine(store.recipients(), hiddenState) { list, hiddenEmails ->
            RecipientBook.merge(list).filterNot { it.origin == Origin.SERVER && it.email.lowercase() in hiddenEmails }
        }

    private fun setHidden(emails: Set<String>) {
        hidden.save(emails)
        hiddenState.value = emails
    }

    override suspend fun addRecipient(email: String, name: String?): SettingsResult {
        val normalized = RecipientBook.normalize(email)
        if (!RecipientBook.isValid(normalized)) return SettingsResult.Invalid("이메일 형식이 올바르지 않습니다")
        return guarded("수신자 추가") {
            val key = normalized.lowercase()
            val isServer = store.recipients().first().any {
                it.email.equals(normalized, true) &&
                    it.origin == Origin.SERVER
            }
            if (isServer && key in hiddenState.value) {
                setHidden(hiddenState.value - key) // 이 차에서 뺐던 회사 지정을 다시 보인다
                SettingsResult.Ok
            } else if (isServer) {
                SettingsResult.Invalid("관리자가 이미 배포한 수신자입니다")
            } else {
                store.saveRecipient(RecipientInfo(normalized, name?.trim()?.ifEmpty { null }, Origin.LOCAL))
                SettingsResult.Ok
            }
        }
    }

    override suspend fun removeRecipient(email: String): SettingsResult {
        val normalized = RecipientBook.normalize(email)
        return guarded("수신자 삭제") {
            val matches = store.recipients().first().filter { it.email.equals(normalized, true) }
            if (matches.isEmpty()) return@guarded SettingsResult.Invalid("없는 수신자입니다")
            matches.firstOrNull { it.origin == Origin.LOCAL }?.let { store.deleteLocalRecipient(it.email) }
            // 회사 지정은 이 차에서만 뺀다 — 다음에 서버가 목록을 보내도 다시 나타나지 않게 기억한다
            if (matches.any { it.origin == Origin.SERVER }) setHidden(hiddenState.value + normalized.lowercase())
            SettingsResult.Ok
        }
    }

    override fun users(): Flow<List<UserInfo>> = store.users().map { list -> list.sortedWith(NAME_ORDER) }

    override suspend fun addUser(name: String, dept: String?, email: String?): SettingsResult {
        val n = name.trim()
        if (n.isEmpty()) return SettingsResult.Invalid("이름을 입력해 주세요")
        val mail = email?.let(RecipientBook::normalize)?.ifEmpty { null }
        if (mail != null && !RecipientBook.isValid(mail)) return SettingsResult.Invalid("이메일 형식이 올바르지 않습니다")
        return guarded("직원 추가") {
            store.saveUser(UserInfo(newId(), n, dept?.trim()?.ifEmpty { null }, mail, Origin.LOCAL))
            SettingsResult.Ok
        }
    }

    override val autoMonthlySend: StateFlow<Boolean> = autoState.asStateFlow()

    override suspend fun setAutoMonthlySend(enabled: Boolean) {
        autoSend.save(enabled)
        autoState.value = enabled
    }

    override suspend fun syncNow(): SyncResult = try {
        relay.syncNow()
    } catch (e: Exception) {
        log("동기화 실패: ${e.message}")
        SyncResult.Failed("서버에 연결하지 못했습니다")
    }

    // ---- 목적·출발→도착 목적 규칙 (F-08) ----

    override fun purposes(): Flow<List<PurposeInfo>> = purposeBook.data.map { purposeBook.purposes(it) }

    override suspend fun addPurpose(label: String, category: PurposeCategory): SettingsResult =
        purposeBook.addPurpose(label, category).toSettings()

    override suspend fun removePurpose(name: String): SettingsResult = purposeBook.removePurpose(name).toSettings()

    override fun purposeRules(): Flow<List<PurposeRuleInfo>> = purposeBook.data.map { d ->
        val places = runCatching { store.places() }.getOrElse { emptyList() }.associateBy { it.placeId }
        purposeBook.rules(d).map { r ->
            PurposeRuleInfo(
                ruleId = r.ruleId,
                originLabel = r.originPlaceId?.let { places[it]?.label },
                originMissing = r.originPlaceId != null && places[r.originPlaceId] == null,
                destLabel = places[r.destPlaceId]?.label,
                purpose = r.purpose,
                origin = r.origin,
            )
        }
    }

    override suspend fun places(): List<PlaceInfo> =
        runCatching { store.places() }.getOrElse { emptyList() }.sortedWith(compareBy(KOREAN) { it.label })

    override suspend fun addPurposeRule(
        originPlaceId: String?,
        destPlaceId: String,
        purposeName: String,
    ): SettingsResult = purposeBook.addRule(originPlaceId, destPlaceId, purposeName).toSettings()

    override suspend fun removePurposeRule(ruleId: String): SettingsResult = purposeBook.removeRule(ruleId).toSettings()

    override suspend fun searchPlaces(query: String): PlaceSearchResult {
        val q = query.trim()
        if (q.length < 2) return PlaceSearchResult.Failed("두 글자 이상 입력해 주세요")
        return try {
            search(q)
        } catch (e: Exception) {
            log("장소 검색 실패: ${e.message}")
            PlaceSearchResult.Failed("장소를 찾지 못했습니다")
        }
    }

    override suspend fun currentPlace(): PlaceSearchResult = try {
        here()?.let { PlaceSearchResult.Found(listOf(it)) } ?: PlaceSearchResult.Failed("지금 위치를 알 수 없어요")
    } catch (e: Exception) {
        log("지금 위치 실패: ${e.message}")
        PlaceSearchResult.Failed("지금 위치를 알 수 없어요")
    }

    override suspend fun addPlace(label: String, lat: Double, lng: Double): PlaceAddResult {
        val name = label.trim()
        if (name.isEmpty()) return PlaceAddResult.Invalid("장소 이름을 입력해 주세요")
        if (name.length > MAX_PLACE_LABEL) return PlaceAddResult.Invalid("장소 이름은 ${MAX_PLACE_LABEL}자 안으로 입력해 주세요")
        return try {
            val places = store.places()
            PlaceMatcher.match(
                lat,
                lng,
                places.map {
                    it.toPlace()
                },
            )?.let { return PlaceAddResult.Added(it.placeId, existing = true) }
            val id = newId()
            store.savePlace(PlaceInfo(id, name, PlaceKind.OTHER, lat, lng, origin = Origin.LOCAL))
            PlaceAddResult.Added(id)
        } catch (e: Exception) {
            log("장소 저장 실패: ${e.message}")
            PlaceAddResult.Invalid("저장하지 못했습니다")
        }
    }

    private fun Result<*>.toSettings(): SettingsResult = fold(
        onSuccess = { SettingsResult.Ok },
        onFailure = { SettingsResult.Invalid(it.message ?: "저장하지 못했습니다") },
    )

    private suspend fun guarded(what: String, block: suspend () -> SettingsResult): SettingsResult = try {
        block()
    } catch (e: Exception) {
        log("$what 실패: ${e.message}")
        SettingsResult.Invalid("저장하지 못했습니다")
    }

    private companion object {
        const val MAX_PLACE_LABEL = 30
        val KOREAN: Collator = Collator.getInstance(Locale.KOREAN)
        val NAME_ORDER: Comparator<UserInfo> =
            Collator.getInstance(Locale.KOREAN).let { c -> Comparator { a, b -> c.compare(a.name, b.name) } }
    }
}

/** 수신자 목록 규칙 (F-24). 순수 함수. */
object RecipientBook {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun isValid(email: String): Boolean = EMAIL.matches(email)

    /** 같은 이메일은 서버 배포분 하나만 남기고, 서버 배포분 → 직접 입력 순, 각각 이메일순. */
    fun merge(list: List<RecipientInfo>): List<RecipientInfo> = list
        .groupBy { normalize(it.email) }
        .map { (_, same) -> same.firstOrNull { it.origin == Origin.SERVER } ?: same.first() }
        .sortedWith(compareBy<RecipientInfo> { it.origin != Origin.SERVER }.thenBy { normalize(it.email) })
}
