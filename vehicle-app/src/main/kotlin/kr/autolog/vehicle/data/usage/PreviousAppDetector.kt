package kr.autolog.vehicle.data.usage

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * AutoLog를 뒤로 보냈을 때 돌아갈 앱이 있는지 판단한다. 앱을 직접 실행하거나 선택하지는 않는다.
 * Android가 다른 앱의 작업 목록을 숨길 수 있으므로, 허용된 작업 목록을 먼저 보고 사용 정보 접근이
 * 허용된 경우 최근 활동 기록을 보조 신호로 쓴다. 둘 다 확인되지 않으면 현재 화면을 유지한다.
 */
class PreviousAppDetector(private val context: Context) {
    fun hasReturnTarget(): Boolean {
        val homePackage = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )?.activityInfo?.packageName
        val settingsPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .resolveActivity(context.packageManager)?.packageName
        val excluded = setOfNotNull(
            context.packageName,
            homePackage,
            settingsPackage,
            "com.android.systemui",
            "com.android.settings",
            "com.android.car.settings",
            "ai.umos.drivingview", // Pleos의 상시 차량 개요는 복귀할 사용자 앱이 아니다.
            "ai.umos.homescreen",
        )
        val tasks = runCatching {
            context.getSystemService(ActivityManager::class.java)?.getRunningTasks(MAX_TASKS).orEmpty()
        }.getOrDefault(emptyList())
        val visibleOtherTask = tasks.any { task ->
            val pkg = task.topActivity?.packageName ?: task.baseActivity?.packageName
            pkg != null && pkg !in excluded
        }
        if (visibleOtherTask) return true
        if (!hasUsageAccess(context)) return false

        val now = System.currentTimeMillis()
        val events = runCatching {
            context.getSystemService(UsageStatsManager::class.java)
                ?.queryEvents(now - RECENT_APP_WINDOW_MS, now)
        }.getOrNull() ?: return false
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg in excluded) continue
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) return true
        }
        return false
    }

    companion object {
        private const val MAX_TASKS = 20
        private const val RECENT_APP_WINDOW_MS = 30 * 60 * 1000L

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ALLOWED
        }
    }
}
