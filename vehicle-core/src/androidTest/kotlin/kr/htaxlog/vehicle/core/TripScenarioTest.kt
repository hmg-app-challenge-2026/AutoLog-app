package kr.htaxlog.vehicle.core

import android.app.Notification
import android.app.NotificationManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.htaxlog.vehicle.session.DistancePolicy
import kr.htaxlog.vehicle.session.TripRecordingService
import kr.htaxlog.vehicle.vehicle.VirtualConsole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId

/**
 * 시연 장면 2·3·6을 실제 Pleos SDK + Room + 서비스로 끝까지 돌린다(에뮬레이터 + Virtual Console).
 * 기어 D로 달리면 운행 자동 시작 → 23.4 km → 기어 P → 5초 뒤 목적 확인(알림) → 목적 확정 → 운행기록부.
 */
@RunWith(AndroidJUnit4::class)
class TripScenarioTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val console = VirtualConsole()

    @After
    fun tearDown() {
        console.stop()
        scope.cancel()
    }

    @Test
    fun driveParkConfirmAndReport(): Unit = runBlocking {
        listOf(
            "pleos.car.permission.FUSED_LOCATION",
            "pleos.car.permission.NAVI_ROUTE",
            "android.permission.POST_NOTIFICATIONS",
        )
            .forEach { runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) } }
        console.start(scope)
        delay(1_500)
        console.setGear("Park")
        val c = HTaxLog.create(context)
        delay(3_000)
        settle(c)

        // 장면 2: 기어 D로 출발하면 운행이 자동으로 시작된다
        val startKm = c.session.totalOdometerKm.value!!
        console.setGear("Drive")
        delay(1_000)
        console.setOdometerKm(add(startKm, "0.5"))
        val recording = withTimeout(15_000) {
            c.session.state.first { it is SessionState.Recording }
        } as SessionState.Recording
        val tripStartKm = recording.trip.startOdometerKm!!
        val endKm = add(tripStartKm, "23.4")
        console.setOdometerKm(endKm)
        withTimeout(10_000) {
            c.session.state.first { (it as? SessionState.Recording)?.liveKm?.let { km -> km >= 23.4 - 1e-9 } == true }
        }
        log("recording ${recording.trip.tripId} start=$tripStartKm end=$endKm")

        // 장면 3: 기어 P → 5초 카운트다운 → 목적 확인
        console.setGear("Park")
        val candidate = withTimeout(10_000) {
            c.session.state.first { it is SessionState.StopCandidate }
        } as SessionState.StopCandidate
        assertEquals(StopReason.GEAR_P, candidate.reason)
        val review = withTimeout(15_000) {
            c.session.state.first { it is SessionState.PendingReview }
        } as SessionState.PendingReview
        assertEquals(DistancePolicy.difference(tripStartKm, endKm), review.distanceKm!!, 1e-9)
        assertEquals("23.4", "%.1f".format(review.distanceKm))
        delay(500)
        assertTrue(notificationText().orEmpty().contains("목적을 확인하세요"))
        assertTrue(c.local.pendingReviews.first().any { it.tripId == review.trip.tripId })

        // 목적 확정 → 이력·운행기록부
        c.session.confirmPurpose(review.trip.tripId, PurposeDetail.CLIENT_VISIT, null, InputMode.TAP)
        assertEquals(PurposeDetail.CLIENT_VISIT, c.local.trip(review.trip.tripId)!!.purpose)
        val month = YearMonth.now(ZoneId.of("Asia/Seoul"))
        val built = c.report.build(month) as ReportBuildResult.Built
        assertTrue(built.file.tripCount >= 1 && built.file.totalKm >= 23.4)
        log("scenario ok: ${review.distanceKm} km → ${built.file.file.name} trips=${built.file.tripCount}")
    }

    /** 앞선 테스트가 남긴 운행을 정리해 Idle에서 시작한다. */
    private suspend fun settle(c: HTaxLogComponents) {
        when (val s = c.session.state.value) {
            is SessionState.Recording, is SessionState.StopCandidate -> c.session.finalize()
            is SessionState.NeedsRecovery -> c.session.resolveRecovery(RecoveryChoice.DELETE)
            else -> Unit
        }
        (c.session.state.value as? SessionState.PendingReview)?.let {
            c.session.confirmPurpose(it.trip.tripId, PurposeDetail.FIELD_WORK, null, InputMode.TAP)
        }
    }

    private fun add(km: Double, plus: String) = BigDecimal(km.toString()).add(BigDecimal(plus)).toDouble()

    private fun notificationText(): String? = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == TripRecordingService.NOTIFICATION_ID }
        ?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun log(msg: String) = Log.i("HTaxLog/probe", msg)
}
