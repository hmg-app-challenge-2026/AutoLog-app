package kr.htaxlog.vehicle.session

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 부팅 뒤 HTaxLog가 조립되고 주기 작업(inbox·월말 발송)이 등록된다. */
@RunWith(AndroidJUnit4::class)
class BootReceiverTest {
    @Test
    fun bootAssemblesHTaxLogAndSchedulesWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val wm = WorkManager.getInstance(context)
        assertTrue(wm.getWorkInfosForUniqueWork("htaxlog-inbox-periodic").get().isNotEmpty())
        assertTrue(wm.getWorkInfosForUniqueWork("htaxlog-monthly-report").get().isNotEmpty())
    }
}
