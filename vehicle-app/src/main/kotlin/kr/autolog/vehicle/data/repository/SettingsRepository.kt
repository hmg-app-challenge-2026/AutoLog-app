package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.autolog.vehicle.core.ConnectionState
import kr.autolog.vehicle.core.DeviceApi
import kr.autolog.vehicle.core.MailApi
import kr.autolog.vehicle.core.MailResult
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.RegisterResult
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SmtpConfig
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.data.upload.UploadApi
import kr.autolog.vehicle.data.upload.UploadResult
import kr.autolog.vehicle.data.upload.UploadStatus

/** 설정(스펙 7.9, F-24·F-01·8장). 화면은 이 인터페이스만 본다. */
interface SettingsRepository {
    val link: StateFlow<ServerLink>

    suspend fun register(serverUrl: String, code: String): Outcome

    val upload: StateFlow<UploadInfo>

    suspend fun uploadNow(): Outcome

    fun recipients(): Flow<List<RecipientInfo>>

    suspend fun addRecipient(email: String, name: String?): Outcome

    suspend fun removeRecipient(email: String): Outcome

    fun users(): Flow<List<UserInfo>>

    suspend fun addUser(name: String, dept: String?, email: String?): Outcome

    val autoMonthlySend: StateFlow<Boolean>

    suspend fun setAutoMonthlySend(enabled: Boolean)

    suspend fun syncNow(): Outcome

    fun smtpConfigured(): Boolean

    suspend fun saveSmtp(config: SmtpConfig): MailResult
}

class DefaultSettingsRepository(
    private val device: DeviceApi,
    private val uploads: UploadApi,
    private val settings: SettingsApi,
    private val mail: MailApi,
    scope: CoroutineScope,
) : SettingsRepository {
    override val link: StateFlow<ServerLink> =
        device.connection.map { it.toLink() }.stateIn(scope, SharingStarted.Eagerly, device.connection.value.toLink())

    override suspend fun register(serverUrl: String, code: String): Outcome =
        when (val r = device.register(serverUrl.trim(), code.trim())) {
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

    override suspend fun addUser(name: String, dept: String?, email: String?): Outcome =
        settings.addUser(name.trim(), dept?.trim()?.ifEmpty { null }, email?.trim()?.ifEmpty { null }).toOutcome()

    override val autoMonthlySend: StateFlow<Boolean> = settings.autoMonthlySend

    override suspend fun setAutoMonthlySend(enabled: Boolean) = settings.setAutoMonthlySend(enabled)

    override suspend fun syncNow(): Outcome = when (val r = settings.syncNow()) {
        is SyncResult.Ok -> Outcome.Ok
        is SyncResult.Failed -> Outcome.Failed(r.reason)
    }

    override fun smtpConfigured(): Boolean = mail.smtpConfigured()

    override suspend fun saveSmtp(config: SmtpConfig): MailResult = mail.saveSmtp(config)

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
