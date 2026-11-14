package kr.autolog.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo

/** [SettingsApi] 가짜. 서버 배포 수신자 1명 + 직접 입력 1명, 직원 3명으로 시작한다. */
class FakeSettingsApi(var nextSync: SyncResult = SyncResult.Ok(received = 2)) : SettingsApi {
    private val recipientState =
        MutableStateFlow(
            listOf(
                RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER),
                RecipientInfo("me@example.com", "나", Origin.LOCAL),
            ),
        )
    private val userState =
        MutableStateFlow(
            listOf(
                UserInfo("u-1", "김영업", "영업1팀"),
                UserInfo("u-2", "이현장", "기술지원팀"),
                UserInfo("u-3", "박관리", "총무팀"),
            ),
        )
    private val autoState = MutableStateFlow(false)

    override fun recipients(): Flow<List<RecipientInfo>> = recipientState

    override suspend fun addRecipient(email: String, name: String?): SettingsResult {
        if (!email.contains('@')) return SettingsResult.Invalid("이메일 형식이 올바르지 않습니다")
        recipientState.value =
            recipientState.value.filterNot { it.email == email } + RecipientInfo(email, name, Origin.LOCAL)
        return SettingsResult.Ok
    }

    override suspend fun removeRecipient(email: String): SettingsResult {
        val r = recipientState.value.firstOrNull { it.email == email } ?: return SettingsResult.Invalid("없는 수신자입니다")
        recipientState.value = recipientState.value - r
        return SettingsResult.Ok
    }

    override fun users(): Flow<List<UserInfo>> = userState

    override suspend fun addUser(name: String, dept: String?, email: String?): SettingsResult {
        if (name.isBlank()) return SettingsResult.Invalid("이름을 입력해 주세요")
        userState.value =
            userState.value + UserInfo("local-${userState.value.size + 1}", name.trim(), dept, email, Origin.LOCAL)
        return SettingsResult.Ok
    }

    override val autoMonthlySend: StateFlow<Boolean> = autoState.asStateFlow()

    override suspend fun setAutoMonthlySend(enabled: Boolean) {
        autoState.value = enabled
    }

    override suspend fun syncNow(): SyncResult = nextSync
}
