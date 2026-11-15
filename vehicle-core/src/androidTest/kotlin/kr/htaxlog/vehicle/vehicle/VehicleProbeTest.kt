package kr.htaxlog.vehicle.vehicle

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Vehicle SDK 탐침 (sdk-findings #1·#2·#5). Pleos 에뮬레이터에서만 돈다. 계측 테스트라 이름은 영어 camelCase(DEX 제약):
 * `./gradlew :vehicle-core:connectedDebugAndroidTest` → `adb logcat -s HTaxLog/probe HTaxLog/vehicle`
 * Virtual Console로 계기판·기어를 바꾸고, SDK 조회·리스너가 그 값을 받는지와 지연을 잰다.
 */
@RunWith(AndroidJUnit4::class)
class VehicleProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val console = VirtualConsole()
    private lateinit var gateway: VehicleGateway

    @Before
    fun setUp(): Unit = runBlocking {
        console.start(scope)
        delay(1_500) // 하트비트가 콘솔을 깨울 때까지
        // 콘솔은 계기판 감소를 거부한다(실차와 같음). 테스트는 "현재 값 + 증가분"으로만 움직인다.
        console.setGear("Park")
        gateway = VehicleGateway(context).also { it.start() }
    }

    @After
    fun tearDown() {
        gateway.stop()
        console.stop()
        scope.cancel()
    }

    /** 서비스 연결 후 계기판을 읽는다 (#1). */
    @Test
    fun connectsAndReadsOdometer(): Unit = runBlocking {
        val t0 = System.currentTimeMillis()

        val first = gateway.awaitReady(attempts = 12, intervalMs = 1_000)

        log("#1 ready in ${System.currentTimeMillis() - t0} ms, odometer=${first?.km}")
        assertNotNull("Vehicle 서비스 연결 또는 Odometer 조회 실패", first)
    }

    /** 계기판이 바뀌면 리스너가 받고, 거리는 소수 1자리로 맞는다 (#2). */
    @Test
    fun odometerListenerReceivesChangeAndDistanceIsExact(): Unit = runBlocking {
        val start = gateway.awaitReady(attempts = 12, intervalMs = 1_000)!!
        val endKm = BigDecimal(start.km.toString()).add(BigDecimal(TRIP_KM)).toDouble()
        val target = async { withTimeout(15_000) { gateway.odometer.first { abs(it.km - endKm) < 1e-6 } } }
        delay(1_000) // 리스너 등록 대기

        val injectedAt = System.currentTimeMillis()
        console.setOdometerKm(endKm)
        val got = target.await()

        log("#2 listener latency ${got.at - injectedAt} ms, km=${got.km}, distance=${"%.1f".format(got.km - start.km)}")
        assertEquals("23.4", "%.1f".format(got.km - start.km))
        assertEquals(endKm, gateway.readOdometer()!!.km, 1e-6)
    }

    /** 기어 P·D를 조회와 리스너로 받는다 (#5). */
    @Test
    fun gearParkAndDriveArriveByReadAndListener(): Unit = runBlocking {
        gateway.awaitReady(attempts = 12, intervalMs = 1_000)
        log("#5 gear now=${gateway.readGear()}")
        val drive = async { withTimeout(15_000) { gateway.gear.first { it == Gear.DRIVE } } }
        delay(1_000)

        val injectedAt = System.currentTimeMillis()
        console.setGear("Drive")
        drive.await()
        log("#5 gear listener latency ${System.currentTimeMillis() - injectedAt} ms")
        val park = async { withTimeout(15_000) { gateway.gear.first { it == Gear.PARK } } }
        delay(1_000)
        console.setGear("Park")

        assertEquals(Gear.PARK, park.await())
        assertEquals(Gear.PARK, gateway.readGear())
    }

    /** 차대번호 제공 여부를 기록한다 (#5). */
    @Test
    fun recordsVinAvailability(): Unit = runBlocking {
        gateway.awaitReady(attempts = 12, intervalMs = 1_000)

        val vin = gateway.readVin()

        log("#5 VIN provided=${vin != null} length=${vin?.length}")
    }

    /** 콘솔 전원 OFF가 AAOS IGNITION_STATE로 오는지 기록한다(시동 OFF 즉시 확정, F-04). 오지 않아도 실패로 보지 않는다. */
    @Test
    fun recordsIgnitionStateFromPowerSimulator(): Unit = runBlocking {
        val props = CarPropertyGateway(context)
        val seen = mutableListOf<Int>()
        val job = launch { props.ignitionState.collect { seen += it } }
        delay(1_000)
        console.setPower("On")
        delay(1_500)
        console.setPower("Off")
        delay(2_000)
        console.setPower("On")
        delay(1_000)
        job.cancel()

        log("#5b ignition states seen=$seen")
    }

    private fun log(msg: String) = Log.i("HTaxLog/probe", msg)

    private companion object {
        const val TRIP_KM = "23.4"
    }
}
