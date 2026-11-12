package kr.autolog.vehicle.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kr.autolog.vehicle.core.DeepLink
import kr.autolog.vehicle.core.TripSessionApi
import kr.autolog.vehicle.session.TripNotificationSpec.Action
import kr.autolog.vehicle.session.TripNotificationSpec.Channel

/**
 * 백그라운드 기록 (F-16). 운행 중 운전자는 내비를 보므로, 앱 화면이 닫혀도 프로세스를 살려 두고 상태를 알림으로 보여 준다.
 * 프로세스가 살아 있는 동안 계속 포그라운드로 둔다 — Android 14+는 백그라운드에서 포그라운드 서비스를 새로 띄우는 것을 막으므로
 * "운행이 시작될 때" 띄우면 주행 중 자동 시작에서 실패할 수 있다. 세션은 [host]로 받는다(AutoLog 조립 지점이 넣는다).
 * [검증 필요] Pleos에서 specialUse 포그라운드 서비스 허용 여부, 헤즈업·카운트다운 표시(V-13).
 */
class TripRecordingService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        val session = host ?: run {
            Log.w(TAG, "no session — stop")
            stopSelf()
            return
        }
        createChannels()
        startInForeground(build(TripNotificationSpec.from(session.state.value, NotificationPresence.appVisible.value)))
        Log.i(TAG, "foreground started")
        follow(session)
    }

    private var followed: TripSessionApi? = null
    private var followJob: Job? = null

    /** [session]의 상태를 알림으로 따라간다. 세션이 바뀌면(다시 조립) 새 세션을 따른다. */
    private fun follow(session: TripSessionApi) {
        if (followed === session) return
        followJob?.cancel()
        followed = session
        followJob = lifecycleScope.launch {
            // 화면이 보이는 동안엔 같은 내용을 조용히(헤즈업 없음, D-37)
            combine(session.state, NotificationPresence.appVisible, TripNotificationSpec::from)
                .collect { notify(build(it)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val session = host
        session?.let { follow(it) }
        when (intent?.action) {
            ACTION_RESUME -> lifecycleScope.launch { session?.resume() }
            ACTION_FINALIZE -> lifecycleScope.launch { session?.finalize() }
        }
        return START_STICKY
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notify(n: Notification) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)

    private fun build(spec: TripNotificationSpec): Notification {
        val channelId = if (spec.channel == Channel.ALERT) CHANNEL_ALERT else CHANNEL_LOW
        val b = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setOngoing(true)
            .setOnlyAlertOnce(spec.channel == Channel.LOW)
            .setCategory(Notification.CATEGORY_STATUS)
            // Android 12+는 포그라운드 서비스 알림을 최대 10초 늦춘다. 기어 P 카운트다운은 즉시 보여야 한다.
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(spec.reviewTripId))
        spec.countdownTo?.let {
            b.setWhen(it).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        spec.actions.forEach { a ->
            val (label, action) = when (a) {
                Action.RESUME -> "계속 기록" to ACTION_RESUME
                Action.FINALIZE -> "기록 종료" to ACTION_FINALIZE
            }
            val pi = PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, TripRecordingService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(null, label, pi).build())
        }
        return b.build()
    }

    /** 앱 첫 화면, 운행 id가 있으면 목적 확인 화면으로(R-3 딥링크). 앱 패키지의 런처 Activity를 연다. */
    private fun openApp(tripId: String?): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (tripId != null) {
            launch.putExtra(DeepLink.KEY_ROUTE, DeepLink.ROUTE_REVIEW).putExtra(DeepLink.KEY_TRIP_ID, tripId)
        }
        return PendingIntent.getActivity(
            this,
            tripId.hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_LOW, "운행 기록", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "운행 종료 안내", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        const val TAG = "AutoLog/session"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_LOW = "trip_recording"
        const val CHANNEL_ALERT = "trip_alert"
        const val ACTION_RESUME = "kr.autolog.vehicle.action.RESUME"
        const val ACTION_FINALIZE = "kr.autolog.vehicle.action.FINALIZE"

        /** 서비스가 따를 세션. AutoLog 조립 지점이 [start] 전에 넣는다. */
        @Volatile var host: TripSessionApi? = null

        /** 앱이 화면에 있을 때(포그라운드) 부른다. 이미 돌고 있으면 아무 일도 없다. */
        fun start(context: Context, session: TripSessionApi) {
            host = session
            context.startForegroundService(Intent(context, TripRecordingService::class.java))
        }
    }
}
