package kr.autolog.vehicle.data.usage

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** vehicle flavor용. AAOS CarUxRestrictionsManager 구독 (스펙 6.9, V-04). 실패하면 null(=PEEK). */
class AndroidCarUxSource(private val context: Context) : CarUxSource {
    private val _state = MutableStateFlow<CarUxState?>(null)
    override val state: StateFlow<CarUxState?> = _state.asStateFlow()
    private var car: Car? = null

    fun start() {
        runCatching {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                Log.w(TAG, "차량 기기가 아님 — CarUxRestrictions 없음, PEEK으로 제한")
                return
            }
            val connected = Car.createCar(context)
            car = connected
            val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _state.value = manager.currentCarUxRestrictions.toState()
            manager.registerListener { restrictions -> _state.value = restrictions.toState() }
        }.onFailure {
            Log.w(TAG, "CarUxRestrictions 연결 실패 — PEEK으로 제한", it)
            _state.value = null
        }
    }

    private fun CarUxRestrictions.toState() = CarUxState(
        requiresDistractionOptimization = isRequiresDistractionOptimization,
        noKeyboard = (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD) != 0,
    )

    private companion object {
        const val TAG = "AutoLog/ui"
    }
}
