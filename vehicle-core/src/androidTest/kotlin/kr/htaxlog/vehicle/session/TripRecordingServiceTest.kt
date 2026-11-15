package kr.htaxlog.vehicle.session

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kr.htaxlog.vehicle.core.SessionState
import kr.htaxlog.vehicle.core.VehicleMotion
import kr.htaxlog.vehicle.fake.FakeTripSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 포그라운드 서비스 + 알림 (에뮬레이터). 알림 액션이 세션을 움직이는지까지 본다. */
@RunWith(AndroidJUnit4::class)
class TripRecordingServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        context.stopService(Intent(context, TripRecordingService::class.java))
        scope.cancel()
    }

    private fun activeText(): String? = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == TripRecordingService.NOTIFICATION_ID }
        ?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    /** 기록 중 알림이 거리를 보여 주고, 기어 P 뒤 [기록 종료] 액션으로 끝낼 수 있다. */
    @Test
    fun foregroundNotificationFollowsSession(): Unit = runBlocking {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
        val session = FakeTripSession(scope, gearPWaitMs = 60_000)
        session.startSession()
        session.addKm(12.3)
        TripRecordingService.start(context, session)
        delay(1_500)
        assertEquals("12.3 km · 판교 테크노밸리 거래처", activeText())

        session.setMotion(VehicleMotion.PARKED)
        delay(500)
        context.startService(
            Intent(context, TripRecordingService::class.java).setAction(TripRecordingService.ACTION_FINALIZE),
        )
        delay(1_000)

        assertTrue(session.state.value is SessionState.PendingReview)
        assertEquals("12.3 km · 목적을 확인하세요", activeText())
    }
}
