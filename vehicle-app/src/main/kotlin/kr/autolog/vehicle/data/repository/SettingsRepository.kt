package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.autolog.vehicle.BuildConfig
import kr.autolog.vehicle.core.ConnectionState
import kr.autolog.vehicle.core.DeviceApi
import kr.autolog.vehicle.core.MailApi
import kr.autolog.vehicle.core.PlaceAddResult
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.PlaceSearchResult
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeInfo
import kr.autolog.vehicle.core.PurposeRuleInfo
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.RegisterResult
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.data.upload.UploadApi
import kr.autolog.vehicle.data.upload.UploadResult
import kr.autolog.vehicle.data.upload.UploadStatus

/** 설정(스펙 7.9, F-24·F-01·8장). 화면은 이 인터페이스만 본다. */
interface SettingsRepository {
    val link: StateFlow<ServerLink>

    /** 연결 코드 6자리로 차를 서버에 등록한다. 서버 주소는 빌드에 고정된 값([DefaultSettingsRepository.serverUrl]). */
    suspend fun register(code: String): Outcome

    val upload: StateFlow<UploadInfo>

    suspend fun uploadNow(): Outcome

    fun recipients(): Flow<List<RecipientInfo>>

    suspend fun addRecipient(email: String, name: String?): Outcome

    suspend fun removeRecipient(email: String): Outcome

    fun users(): Flow<List<UserInfo>>

    /** 목적 목록·출발→도착 목적 규칙(F-08). 회사 지정은 관리자 웹에서, 이 차 것은 여기서 더한다. */
    fun purposes(): Flow<List<PurposeInfo>>

    suspend fun addPurpose(label: String, category: PurposeCategory): Outcome

    suspend fun removePurpose(name: String): Outcome

    fun purposeRules(): Flow<List<PurposeRuleInfo>>

    suspend fun places(): List<PlaceInfo>

    suspend fun addPurposeRule(originPlaceId: String?, destPlaceId: String, purposeName: String): Outcome

    /** 새 장소 찾기·지금 위치·저장(규칙 팝업의 [+ 새 장소]). */
    suspend fun searchPlaces(query: String): PlaceSearchResult

    suspend fun currentPlace(): PlaceSearchResult

    suspend fun addPlace(label: String, lat: Double, lng: Double): PlaceAddResult

    suspend fun removePurposeRule(ruleId: String): Outcome

    suspend fun addUser(name: String, dept: String?): Outcome

    val autoMonthlySend: StateFlow<Boolean>

    suspend fun setAutoMonthlySend(enabled: Boolean)

    suspend fun syncNow(): Outcome

    /** 연결된 발신 메일 주소. 메일 계정은 관리자 웹에서 정해 서버로 온다 — 차에서는 입력하지 않는다(2026-11-14). */
    val mailAccount: StateFlow<String?>
}

class DefaultSettingsRepository(
    private val device: DeviceApi,
    private val uploads: UploadApi,
    private val settings: SettingsApi,
    private val mail: MailApi,
    scope: CoroutineScope,
    /** 중계 서버 주소. 서버는 하나라 화면에서 묻지 않는다(2026-11-13) — 기본은 빌드 설정(local.properties autolog.relayUrl). */
    private val serverUrl: String = BuildConfig.RELAY_BASE_URL,
) : SettingsRepository {
    override val link: StateFlow<ServerLink> =
        device.connection.map { it.toLink() }.stateIn(scope, SharingStarted.Eagerly, device.connection.value.toLink())

    override suspend fun register(code: String): Outcome = when (val r = device.register(serverUrl, code.trim())) {
        RegisterResult.Ok -> Outcome.Ok
        is RegisterResult.Invalid -> Outcome.Failed(r.reason)
    }

    override val upload: StateFlow<UploadInfo> =
        uploads.status.map { it.toInfo() }.stateIn(scope, SharingStarted.Eagerly, uploads.status.value.toInfo())

    override suspend fun uploadNow(): Outcome = when (val r = uploads.uploadNow()) {
        is UploadResult.Uploaded -> Outcome.Ok
        is UploadResult.Failed -> Outcome.Failed(r.reason)
    }

    override fun recipients(): Flow<List<RecipientInfo>> = settings.recipients()

    override suspend fun addRecipient(email: String, name: String?): Outcome =
        settings.addRecipient(email.trim(), name?.trim()).toOutcome()

    override suspend fun removeRecipient(email: String): Outcome = settings.removeRecipient(email).toOutcome()

    override fun users(): Flow<List<UserInfo>> = settings.users()

    override fun purposes(): Flow<List<PurposeInfo>> = settings.purposes()

    override suspend fun addPurpose(label: String, category: PurposeCategory): Outcome =
        settings.addPurpose(label.trim(), category).toOutcome()

    override suspend fun removePurpose(name: String): Outcome = settings.removePurpose(name).toOutcome()

    override fun purposeRules(): Flow<List<PurposeRuleInfo>> = settings.purposeRules()

    override suspend fun places(): List<PlaceInfo> = settings.places()

    override suspend fun addPurposeRule(originPlaceId: String?, destPlaceId: String, purposeName: String): Outcome =
        settings.addPurposeRule(originPlaceId, destPlaceId, purposeName).toOutcome()

    override suspend fun removePurposeRule(ruleId: String): Outcome = settings.removePurposeRule(ruleId).toOutcome()

    override suspend fun searchPlaces(query: String): PlaceSearchResult = settings.searchPlaces(query)

    override suspend fun currentPlace(): PlaceSearchResult = settings.currentPlace()

    override suspend fun addPlace(label: String, lat: Double, lng: Double): PlaceAddResult =
        settings.addPlace(label, lat, lng)

    // 직원 이메일은 받지 않는다(2026-11-15 — 쓰는 곳이 없는 개인정보). 계약은 그대로 두고 null을 넘긴다
    override suspend fun addUser(name: String, dept: String?): Outcome =
        settings.addUser(name.trim(), dept?.trim()?.ifEmpty { null }, null).toOutcome()

    override val autoMonthlySend: StateFlow<Boolean> = settings.autoMonthlySend

    override suspend fun setAutoMonthlySend(enabled: Boolean) = settings.setAutoMonthlySend(enabled)

    override suspend fun syncNow(): Outcome = when (val r = settings.syncNow()) {
        is SyncResult.Ok -> Outcome.Ok
        is SyncResult.Failed -> Outcome.Failed(r.reason)
    }

    override val mailAccount: StateFlow<String?> = mail.account

    private fun SettingsResult.toOutcome() = when (this) {
        SettingsResult.Ok -> Outcome.Ok
        is SettingsResult.Invalid -> Outcome.Failed(reason)
    }

    private fun ConnectionState.toLink(): ServerLink = when (this) {
        ConnectionState.NotRegistered -> ServerLink.NotLinked
        is ConnectionState.Registered -> ServerLink.Linked(vehicleLabel, serverUrl, lastSyncAt)
        is ConnectionState.Unreachable -> ServerLink.Unreachable(vehicleLabel, serverUrl)
    }

    private fun UploadStatus.toInfo() = UploadInfo(enabled, pendingCount, lastUploadAt, lastError)
}
