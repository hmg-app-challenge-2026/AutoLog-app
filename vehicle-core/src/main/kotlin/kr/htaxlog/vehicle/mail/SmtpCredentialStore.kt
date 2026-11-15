package kr.htaxlog.vehicle.mail

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kr.htaxlog.vehicle.core.SmtpConfig

/** SMTP 계정 저장소 (F-24). 테스트에서 메모리 구현으로 바꿀 수 있게 인터페이스로 둔다. */
interface SmtpCredentialStore {
    fun load(): SmtpConfig?

    fun save(config: SmtpConfig)

    fun clear()
}

/**
 * EncryptedSharedPreferences에 저장한다. 키는 Android Keystore의 마스터 키로 암호화되어
 * 앱 비밀번호가 평문으로 디스크에 남지 않는다 (F-24 수용 기준).
 */
class EncryptedSmtpCredentialStore(context: Context) : SmtpCredentialStore {
    private val prefs: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    override fun load(): SmtpConfig? {
        val host = prefs.getString(HOST, null) ?: return null
        val username = prefs.getString(USERNAME, null) ?: return null
        val password = prefs.getString(PASSWORD, null) ?: return null
        return SmtpConfig(
            host = host,
            port = prefs.getInt(PORT, DEFAULT_PORT),
            username = username,
            password = password,
            fromName = prefs.getString(FROM_NAME, null),
            startTls = prefs.getBoolean(START_TLS, true),
        )
    }

    override fun save(config: SmtpConfig) {
        prefs
            .edit()
            .putString(HOST, config.host)
            .putInt(PORT, config.port)
            .putString(USERNAME, config.username)
            .putString(PASSWORD, config.password)
            .putString(FROM_NAME, config.fromName)
            .putBoolean(START_TLS, config.startTls)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val DEFAULT_PORT = 587
        const val FILE_NAME = "htaxlog_smtp"
        const val HOST = "host"
        const val PORT = "port"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val FROM_NAME = "from_name"
        const val START_TLS = "start_tls"
    }
}
