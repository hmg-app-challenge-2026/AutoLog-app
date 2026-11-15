package kr.htaxlog.vehicle.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** 이 차의 서버 등록 정보. 기기 토큰은 비밀값이라 [toString]에서 가린다(CONVENTIONS 7.6). */
data class DeviceRecord(
    val serverUrl: String,
    val token: String,
    val vehicleId: String,
    val orgId: String,
    val plateNo: String? = null,
    val model: String? = null,
    /** 마지막 inbox 수신 성공 시각. */
    val lastSyncAt: Long? = null,
) {
    override fun toString() =
        "DeviceRecord(serverUrl=$serverUrl, token=***, vehicleId=$vehicleId, plateNo=$plateNo, lastSyncAt=$lastSyncAt)"
}

interface DeviceStore {
    fun load(): DeviceRecord?

    fun save(record: DeviceRecord)

    fun clear()
}

/** 기기 토큰을 EncryptedSharedPreferences에 둔다. 평문으로 디스크에 남지 않는다(F-01 "기기 토큰 암호화 저장"). */
class EncryptedDeviceStore(context: Context) : DeviceStore {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): DeviceRecord? {
        val url = prefs.getString(URL, null) ?: return null
        val token = prefs.getString(TOKEN, null) ?: return null
        return DeviceRecord(
            serverUrl = url,
            token = token,
            vehicleId = prefs.getString(VEHICLE_ID, null) ?: return null,
            orgId = prefs.getString(ORG_ID, null).orEmpty(),
            plateNo = prefs.getString(PLATE_NO, null),
            model = prefs.getString(MODEL, null),
            lastSyncAt = prefs.getLong(LAST_SYNC_AT, -1).takeIf { it >= 0 },
        )
    }

    override fun save(record: DeviceRecord) {
        prefs.edit()
            .putString(URL, record.serverUrl)
            .putString(TOKEN, record.token)
            .putString(VEHICLE_ID, record.vehicleId)
            .putString(ORG_ID, record.orgId)
            .putString(PLATE_NO, record.plateNo)
            .putString(MODEL, record.model)
            .putLong(LAST_SYNC_AT, record.lastSyncAt ?: -1)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "htaxlog_device"
        const val URL = "server_url"
        const val TOKEN = "device_token"
        const val VEHICLE_ID = "vehicle_id"
        const val ORG_ID = "org_id"
        const val PLATE_NO = "plate_no"
        const val MODEL = "model"
        const val LAST_SYNC_AT = "last_sync_at"
    }
}
