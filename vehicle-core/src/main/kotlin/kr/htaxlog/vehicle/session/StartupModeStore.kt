package kr.htaxlog.vehicle.session

import android.content.Context

/** 시동 후 앱 화면을 열지, 기록 엔진만 준비할지 이 차량 앱의 로컬 설정에 저장한다. */
enum class StartupMode { SCREEN, BACKGROUND }

const val EXTRA_SCREEN_ON_BOOT = "kr.htaxlog.vehicle.SCREEN_ON_BOOT"

interface StartupModeStore {
    fun get(): StartupMode

    fun set(mode: StartupMode)
}

class SharedPreferencesStartupModeStore(context: Context) : StartupModeStore {
    private val preferences = context.applicationContext.getSharedPreferences("htaxlog_startup", Context.MODE_PRIVATE)

    override fun get(): StartupMode = when (preferences.getString("mode", null)) {
        StartupMode.SCREEN.name -> StartupMode.SCREEN
        else -> StartupMode.BACKGROUND
    }

    override fun set(mode: StartupMode) {
        preferences.edit().putString("mode", mode.name).commit()
    }
}
