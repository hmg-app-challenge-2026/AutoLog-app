package kr.htaxlog.vehicle.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * AAOS 표준 차량 속성 (프론트 스펙 1.1.1 4순위). Pleos SDK 2.3.2에 시동 상태·차속 공개 API가 없어 `CarPropertyManager`로 읽는다.
 * 권한: 시동 `android.car.permission.CAR_POWERTRAIN`, 차속 `android.car.permission.CAR_SPEED`(런타임 권한).
 * 차량 OS가 없거나 권한이 없거나 속성을 못 읽으면 흐름이 조용히 끝난다 — 세션은 계기판만으로 판단한다.
 */
class CarPropertyGateway(context: Context) {
    private val props: CarPropertyManager? = runCatching {
        Car.createCar(context.applicationContext)?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
    }.onFailure { Log.w(TAG, "car service unavailable: ${it.message}") }.getOrNull()

    /** IGNITION_STATE 원본 값(0 UNDEFINED, 1 LOCK, 2 OFF, 3 ACC, 4 ON, 5 START). */
    val ignitionState: Flow<Int> = callbackFlow {
        val pm = props
        if (pm == null) {
            close()
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                (value.value as? Int)?.let {
                    Log.d(TAG, "ignition=$it")
                    trySend(it)
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "ignition error area=$areaId")
            }
        }
        val ok = runCatching {
            pm.registerCallback(callback, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }.onFailure { Log.w(TAG, "ignition register failed: ${it.message}") }.getOrDefault(false)
        if (!ok) close()
        awaitClose { runCatching { pm.unregisterCallback(callback) } }
    }

    /**
     * 차속(km/h). AAOS `PERF_VEHICLE_SPEED`(m/s, Float)를 km/h로 바꾼다. 거리 계산에는 쓰지 않고 "움직이는 중"
     * 판단에만 쓴다 — 계기판은 0.1 km 단위라 저속에서 10초 넘게 안 바뀔 수 있다. [검증 필요] 실차 갱신 주기.
     */
    val speedKph: Flow<Double> = callbackFlow {
        val pm = props
        if (pm == null) {
            close()
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val mps = (value.value as? Float) ?: return
                if (mps.isNaN() || mps.isInfinite()) return
                trySend(mps * MPS_TO_KPH)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "speed error area=$areaId")
            }
        }
        val ok = runCatching {
            pm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_RATE_HZ)
        }.onFailure { Log.w(TAG, "speed register failed: ${it.message}") }.getOrDefault(false)
        if (!ok) close() else Log.i(TAG, "speed listener registered")
        awaitClose { runCatching { pm.unregisterCallback(callback) } }
    }

    /** 시동이 꺼지는 순간. */
    val ignitionOff: Flow<Unit> = ignitionState.filter { isOff(it) }.map { }

    companion object {
        private const val TAG = "HTaxLog/vehicle"
        private const val LOCK = 1
        private const val OFF = 2
        private const val ACC = 3
        private const val MPS_TO_KPH = 3.6
        private const val SPEED_RATE_HZ = 1f

        fun isOff(state: Int) = state == LOCK || state == OFF || state == ACC
    }
}
