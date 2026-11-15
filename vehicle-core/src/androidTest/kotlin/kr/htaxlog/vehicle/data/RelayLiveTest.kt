package kr.htaxlog.vehicle.data

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.htaxlog.vehicle.core.ConnectionState
import kr.htaxlog.vehicle.core.QrResult
import kr.htaxlog.vehicle.core.RegisterResult
import kr.htaxlog.vehicle.core.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 relay 서버로 시연 장면 1(연결 코드 등록 → inbox 수신 → QR 토큰). 수동 실행 전용 — 인자가 없으면 건너뛴다.
 * 맥에서 `cd ops && docker compose up -d relay postgres minio` 후 운영 웹(또는 curl)으로 차량·직원·연결 코드를 만들고:
 * `./gradlew :vehicle-core:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=kr.htaxlog.vehicle.data.RelayLiveTest
 *  -Pandroid.testInstrumentationRunnerArguments.relayCode=<코드>` (에뮬레이터에서 맥은 10.0.2.2)
 */
@RunWith(AndroidJUnit4::class)
class RelayLiveTest {
    @Test
    fun registerSyncAndIssueQrAgainstRealRelay(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val code = args.getString("relayCode")
        assumeTrue("relayCode 인자가 없어 건너뜀", code != null)
        val url = args.getString("relayUrl") ?: "http://10.0.2.2:8080"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HTaxLogDb::class.java).build()
        val store = object : DeviceStore {
            var r: DeviceRecord? = null
            override fun load() = r
            override fun save(record: DeviceRecord) {
                r = record
            }
            override fun clear() {
                r = null
            }
        }
        val device = DeviceRepository(store, RelayHttp())
        val sync = InboxSync(device, db.referenceDao(), db.expenseDao(), db.inboxDao())
        device.onRegistered = { sync.syncNow() }

        val reg = device.register(url, code!!)
        Log.i("HTaxLog/probe", "live register=$reg connection=${device.connection.value}")
        assertEquals(RegisterResult.Ok, reg)

        val users = db.referenceDao().observeUsers().first()
        val again = sync.syncNow()
        val qr = device.issueQr()
        Log.i("HTaxLog/probe", "live users=${users.map { it.name }} resync=$again qr=$qr")
        assertTrue(device.connection.value is ConnectionState.Registered)
        assertTrue(again is SyncResult.Ok)
        assertTrue(qr is QrResult.Issued)
        db.close()
    }
}
