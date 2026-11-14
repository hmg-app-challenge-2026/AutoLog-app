package kr.autolog.vehicle.mail

import android.content.Context
import kr.autolog.vehicle.domain.HiddenRecipientStore

/** 이 차에서 뺀 회사 지정 받는 사람 (F-24). 비밀값이 아니라 일반 SharedPreferences에 둔다. */
class PrefsHiddenRecipients(context: Context) : HiddenRecipientStore {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun save(emails: Set<String>) {
        prefs.edit().putStringSet(KEY, emails).apply()
    }

    private companion object {
        const val FILE_NAME = "autolog_settings"
        const val KEY = "hidden_server_recipients"
    }
}
