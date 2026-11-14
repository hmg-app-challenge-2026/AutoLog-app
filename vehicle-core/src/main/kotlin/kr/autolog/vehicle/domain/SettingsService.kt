package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.Origin
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

    private suspend fun guarded(what: String, block: suspend () -> SettingsResult): SettingsResult = try {
        block()
    } catch (e: Exception) {
        log("$what 실패: ${e.message}")
        SettingsResult.Invalid("저장하지 못했습니다")
    }

    private companion object {
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
