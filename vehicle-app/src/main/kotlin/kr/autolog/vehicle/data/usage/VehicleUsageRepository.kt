package kr.autolog.vehicle.data.usage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.autolog.vehicle.core.VehicleMotion

/** 사용 단계의 유일한 진실 (스펙 6.9 1번). */
interface VehicleUsageRepository {
    val tier: StateFlow<UsageTier>
    val noKeyboard: StateFlow<Boolean>
}

class DefaultVehicleUsageRepository(
    motion: StateFlow<VehicleMotion?>,
    carUx: StateFlow<CarUxState?>,
    scope: CoroutineScope,
) : VehicleUsageRepository {
    override val tier: StateFlow<UsageTier> =
        combine(motion, carUx, ::usageTierOf)
            .stateIn(scope, SharingStarted.Eagerly, usageTierOf(motion.value, carUx.value))

    override val noKeyboard: StateFlow<Boolean> =
        carUx.map { it?.noKeyboard ?: true }
            .stateIn(scope, SharingStarted.Eagerly, carUx.value?.noKeyboard ?: true)
}
