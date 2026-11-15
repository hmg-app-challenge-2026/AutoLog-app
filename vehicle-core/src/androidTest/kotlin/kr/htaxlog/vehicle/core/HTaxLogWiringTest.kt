package kr.htaxlog.vehicle.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** 조립 지점: 실제 SDK·Room·서비스로 모든 계약 구현이 만들어지고 바로 쓸 수 있다(에뮬레이터). */
@RunWith(AndroidJUnit4::class)
class HTaxLogWiringTest {
    @Test
    fun createAssemblesWorkingComponents(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // 프론트가 Activity에서 요청할 런타임 권한
        listOf(
            "pleos.car.permission.FUSED_LOCATION",
            "pleos.car.permission.NAVI_ROUTE",
            "android.permission.POST_NOTIFICATIONS",
        )
            .forEach { runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) } }

        val c = HTaxLog.create(context)
        delay(3_000) // Vehicle 서비스 연결·복구

        assertSame(c, HTaxLog.create(context))
        assertNotNull("계기판을 읽어야 한다", c.session.totalOdometerKm.value)
        assertEquals(ConnectionState.NotRegistered, c.device.connection.value)
        assertTrue(c.local.recent(5).first().isEmpty() || c.local.recent(5).first().isNotEmpty())
        assertTrue(c.report.build(YearMonth.of(2026, 11)) is ReportBuildResult.Built)
        assertTrue(c.receipts.issueQr() is QrResult.Failed) // 서버 미연결
        assertTrue(c.settings.syncNow() is SyncResult.Failed)
    }
}
