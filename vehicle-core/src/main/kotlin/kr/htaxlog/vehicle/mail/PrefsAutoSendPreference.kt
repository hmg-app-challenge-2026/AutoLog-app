package kr.htaxlog.vehicle.mail

import android.content.Context
import kr.htaxlog.vehicle.domain.AutoSendPreference

/** 월말 자동 생성·발송 스위치 (F-24). 비밀값이 아니라 일반 SharedPreferences에 둔다. */
class PrefsAutoSendPreference(context: Context) : AutoSendPreference {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): Boolean = prefs.getBoolean(KEY, false)

    override fun save(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    private companion object {
        const val FILE_NAME = "htaxlog_settings"
        const val KEY = "auto_monthly_send"
    }
}
