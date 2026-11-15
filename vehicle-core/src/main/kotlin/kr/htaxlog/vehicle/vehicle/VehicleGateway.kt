package kr.htaxlog.vehicle.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.retryWhen
import kr.htaxlog.vehicle.core.sdkCall
import kr.htaxlog.vehicle.core.sdkListenerFlow
import java.time.Clock

/**
 * Pleos Vehicle SDK 2.3.2 게이트웨이 (F-01, F-04, F-05, F-13). `ai.pleos.*` import는 이 패키지에만 둔다.
 *
 * 호출 순서: [start] → [awaitReady] → 조회·리스너 → [stop]. `Vehicle.initialize()`는 완료 콜백이 없으므로
 * 첫 조회가 성공할 때까지 [awaitReady]로 다시 시도한다. 권한: `pleos.car.permission.CAR_DRIVE`(Odometer·Gear),
 * `CAR_INFO`(VIN), `CAR_ENERGY`(Fuel). 시그니처는 AAR로 확인했다. 에뮬레이터 동작은 sdk-findings #1·#2·#5 [검증 필요]
 */
class VehicleGateway(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VehicleSignals {
    private val vehicle = Vehicle(context.applicationContext)
    private val carProps = CarPropertyGateway(context)

    @Volatile private var odometerReadable: Boolean? = null

    fun start() = vehicle.initialize()

    fun stop() = vehicle.release()

    /** 서비스 연결을 기다린다. 첫 Odometer 조회가 성공하면 그 값을, 끝내 실패하면 null(F-01 "지원되지 않는 차량"). */
    suspend fun awaitReady(attempts: Int = READY_ATTEMPTS, intervalMs: Long = READY_INTERVAL_MS): OdometerReading? =
        retryUntilValue(attempts, intervalMs) { readOdometer() }

    override suspend fun readOdometer(): OdometerReading? = try {
        val odo = vehicle.getOdometer()
        if (odometerReadable == null) {
            val cap = sdkCall { ok, fail -> odo.checkCurrentDistanceDrivenCapabilities(ok, fail) }
            odometerReadable = cap.isReadable()
            Log.i(TAG, "odometer capability=${cap.accessType}")
        }
        if (odometerReadable == true) {
            val raw: Float? = sdkCall { ok, fail -> odo.getCurrentDistanceDriven(ok, fail) }
            Log.d(TAG, "odometer raw=$raw")
            raw?.toOdometerKm()?.let { OdometerReading(it, clock.millis()) }
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "odometer read failed: ${e.message}")
        null
    }

    override val odometer: Flow<OdometerReading> =
        sdkListenerFlow<DistanceDrivenListener, Float?>(
            register = { vehicle.getOdometer().registerDistanceDriven(it) },
            unregister = { vehicle.getOdometer().unregisterDistanceDriven(it) },
            makeListener = { emit, fail ->
                object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(value: Float?) = emit(value)

                    override fun onFailed(e: Exception) = fail(e)
                }
            },
        ).mapNotNull { raw -> raw?.toOdometerKm()?.let { OdometerReading(it, clock.millis()) } }
            .resilient("odometer")

    override suspend fun readGear(): Gear? = try {
        val g: VehicleGear? = sdkCall { ok, fail -> vehicle.getGear().getCurrentGearSelectionState(ok, fail) }
        Log.d(TAG, "gear raw=$g")
        g?.toGear()?.takeIf { it != Gear.UNKNOWN }
    } catch (e: Exception) {
        Log.w(TAG, "gear read failed: ${e.message}")
        null
    }

    override val gear: Flow<Gear> =
        sdkListenerFlow<GearSelectionListener, VehicleGear?>(
            register = { vehicle.getGear().registerGearSelection(it) },
            unregister = { vehicle.getGear().unregisterGearSelection(it) },
            makeListener = { emit, fail ->
                object : GearSelectionListener {
                    override fun onSelected(gear: VehicleGear?) = emit(gear)

                    override fun onFailed(e: Exception) = fail(e)
                }
            },
        ).mapNotNull { it?.toGear() }
            .filter { it != Gear.UNKNOWN }
            .resilient("gear")

    override val ignitionOff: Flow<Unit> = carProps.ignitionOff.flowOn(dispatcher)

    override val speedKph: Flow<Double> = carProps.speedKph.flowOn(dispatcher)

    /** 차대번호. 설정 화면의 차량 식별 보조(F-01). 못 읽으면 null. */
    suspend fun readVin(): String? = try {
        val info = vehicle.getCarInfo()
        val cap = sdkCall { ok, fail -> info.checkVINInfoCapabilities(ok, fail) }
        if (cap.isReadable()) sdkCall<String?> { ok, fail -> info.getVINInfo(ok, fail) }?.ifBlank { null } else null
    } catch (e: Exception) {
        Log.w(TAG, "vin read failed: ${e.message}")
        null
    }

    /** 연료량(%) 변화. 주유 이벤트 보조 신호(F-18). */
    val fuelPercent: Flow<Int> =
        sdkListenerFlow<FuelPercentListener, Int?>(
            register = { vehicle.getFuel().registerFuelPercent(it) },
            unregister = { vehicle.getFuel().unregisterFuelPercent(it) },
            makeListener = { emit, fail ->
                object : FuelPercentListener {
                    override fun onFuelPercentUpdated(value: Int?) = emit(value)

                    override fun onFailed(e: Exception) = fail(e)
                }
            },
        ).mapNotNull { it }
            .resilient("fuel")

    /** 리스너가 실패하면 잠시 뒤 다시 등록하고, 그래도 안 되면 조용히 끝낸다. SDK 콜백은 [dispatcher]로 옮긴다. */
    private fun <T> Flow<T>.resilient(name: String): Flow<T> = retryWhen { cause, attempt ->
        Log.w(TAG, "$name listener failed (attempt ${attempt + 1}): ${cause.message}")
        if (attempt < LISTENER_RETRIES) {
            delay(READY_INTERVAL_MS)
            true
        } else {
            false
        }
    }.catch { Log.w(TAG, "$name listener gave up: ${it.message}") }
        .flowOn(dispatcher)

    private fun CapabilityConfig.isReadable() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    companion object {
        const val TAG = "HTaxLog/vehicle"
        const val READY_ATTEMPTS = 12
        const val READY_INTERVAL_MS = 5_000L
        private const val LISTENER_RETRIES = 3
    }
}

/** SDK 기어를 HTaxLog 기어로. 수동 1~9단은 전진(DRIVE)이다. */
internal fun VehicleGear.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.PARK
    VehicleGear.GEAR_REVERSE -> Gear.REVERSE
    VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
    VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    else -> Gear.DRIVE
}
