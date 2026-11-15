package kr.htaxlog.vehicle.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 기기 토큰이 암호화돼 저장되고 다시 읽힌다(평문 파일에 토큰이 없다). */
@RunWith(AndroidJUnit4::class)
class EncryptedDeviceStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tokenRoundTripsAndIsNotPlainText() {
        val store = EncryptedDeviceStore(context)
        store.save(
            DeviceRecord("https://htaxlog.acme.co.kr", "secret-token-123", "veh-1", "org-1", "12가3456", lastSyncAt = 5),
        )

        assertEquals("secret-token-123", store.load()!!.token)
        assertEquals(5L, store.load()!!.lastSyncAt)
        val raw = File(context.applicationInfo.dataDir, "shared_prefs/htaxlog_device.xml").readText()
        assertFalse("secret-token-123" in raw)

        store.clear()
        assertNull(store.load())
    }
}
