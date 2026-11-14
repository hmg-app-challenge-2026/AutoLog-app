package kr.autolog.vehicle.mail

import android.content.Context
import android.util.Log
import kr.autolog.shared.AutoLogJson
import kr.autolog.vehicle.domain.PurposeBookData
import kr.autolog.vehicle.domain.PurposeBookStore

/** 목적·목적 규칙 (F-08) 저장. 비밀값이 아니라 일반 SharedPreferences에 JSON 한 덩어리로 둔다. */
class PrefsPurposeBookStore(context: Context) : PurposeBookStore {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): PurposeBookData {
        val text = prefs.getString(KEY, null) ?: return PurposeBookData()
        return runCatching { AutoLogJson.decodeFromString(PurposeBookData.serializer(), text) }
            .onFailure { Log.w(TAG, "목적 목록을 읽지 못해 비움: ${it.message}") }
            .getOrDefault(PurposeBookData())
    }

    override fun save(data: PurposeBookData) {
        prefs.edit().putString(KEY, AutoLogJson.encodeToString(PurposeBookData.serializer(), data)).apply()
    }

    private companion object {
        const val FILE_NAME = "autolog_settings"
        const val KEY = "purpose_book"
        const val TAG = "AutoLog/domain"
    }
}
