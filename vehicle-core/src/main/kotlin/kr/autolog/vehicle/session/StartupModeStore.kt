package kr.autolog.vehicle.session

import android.content.Context

/** 시동 후 앱 화면을 열지, 기록 엔진만 준비할지 차량 사용자별로 저장한다. */
enum class StartupMode { SCREEN, BACKGROUND }

const val EXTRA_SCREEN_ON_BOOT = "kr.autolog.vehicle.SCREEN_ON_BOOT"

interface StartupModeStore {
    fun get(): StartupMode

    fun set(mode: StartupMode)
}

class SharedPreferencesStartupModeStore(context: Context) : StartupModeStore {
    private val preferences = context.applicationContext.getSharedPreferences("autolog_startup", Context.MODE_PRIVATE)

    override fun get(): StartupMode = when (preferences.getString("mode", null)) {
        StartupMode.SCREEN.name -> StartupMode.SCREEN
        else -> StartupMode.BACKGROUND
    }

    override fun set(mode: StartupMode) {
        preferences.edit().putString("mode", mode.name).commit()
    }
}
