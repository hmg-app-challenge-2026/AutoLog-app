package kr.autolog.vehicle.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import kr.autolog.vehicle.core.VehicleMotion

/** Android 일반 앱은 다른 앱의 작업 존속 여부를 확인할 수 없다. 최근 실행 기록은 복귀 후보일 뿐이다. */
enum class ReturnTargetStatus { RECENT_APP, NO_RECENT_APP, UNKNOWN }

/** 최초 화면 진입은 Root가 판단하고, 같은 주행 상태로 화면에 재진입할 때도 한 번 더 판단한다. */
class AppReturnController(private val gateway: AppReturnGateway, private val background: () -> Unit) {
    private var resumedBefore = false

    fun onMotionConfirmed() {
        if (gateway.targetStatus() == ReturnTargetStatus.RECENT_APP) background()
    }

    fun onResume(motion: VehicleMotion?) {
        if (resumedBefore && motion == VehicleMotion.MOVING) onMotionConfirmed()
        resumedBefore = true
    }
}

/** 앱 전환 판단과 사용 정보 접근 설정을 화면·Activity에서 분리한 경계. */
interface AppReturnGateway {
    val supported: Boolean

    fun targetStatus(): ReturnTargetStatus

    fun hasUsageAccess(): Boolean

    fun openSettings(): Boolean

    /** 데모는 이전부터 주행 시 뒤로 물러나는 동작을 유지한다. */
    data object Demo : AppReturnGateway {
        override val supported = false

        override fun targetStatus() = ReturnTargetStatus.RECENT_APP

        override fun hasUsageAccess() = false

        override fun openSettings() = false
    }
}

/** Pleos가 선택할 이전 화면의 후보가 최근 사용 기록에 있는지만 확인한다. 특정 앱을 실행하지 않는다. */
class AndroidAppReturnGateway(context: Context) : AppReturnGateway {
    private val app = context.applicationContext
    override val supported = true

    override fun targetStatus(): ReturnTargetStatus {
        if (!hasUsageAccess()) return ReturnTargetStatus.UNKNOWN
        val excluded = systemPackages()
        val now = System.currentTimeMillis()
        val events = runCatching {
            app.getSystemService(UsageStatsManager::class.java)?.queryEvents(now - RECENT_APP_WINDOW_MS, now)
        }.getOrNull() ?: return ReturnTargetStatus.UNKNOWN
        val event = UsageEvents.Event()
        var recentApp = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN) {
                recentApp = false
                continue
            }
            val pkg = event.packageName ?: continue
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && pkg !in excluded) recentApp = true
        }
        return if (recentApp) ReturnTargetStatus.RECENT_APP else ReturnTargetStatus.NO_RECENT_APP
    }

    override fun hasUsageAccess(): Boolean {
        val appOps = app.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun openSettings(): Boolean = try {
        app.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "usage access settings unavailable", error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, "usage access settings denied", error)
        false
    }

    private fun systemPackages(): Set<String> {
        val home = app.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )?.activityInfo?.packageName
        val settings = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .resolveActivity(app.packageManager)?.packageName
        return setOfNotNull(
            app.packageName,
            home,
            settings,
            "com.android.systemui",
            "com.android.settings",
            "com.android.car.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "ai.umos.drivingview",
            "ai.umos.homescreen",
        )
    }

    private companion object {
        const val TAG = "AutoLog/app-return"
        const val RECENT_APP_WINDOW_MS = 30 * 60 * 1000L
    }
}
