package kr.autolog.vehicle.data.usage

import kr.autolog.vehicle.core.VehicleMotion

/** 스펙 1.1. 순서 = 제한이 강한 순(ordinal이 작을수록 제한적). */
enum class UsageTier { BACKGROUND, PEEK, FULL }

/** AAOS CarUxRestrictions에서 필요한 값만. */
data class CarUxState(val requiresDistractionOptimization: Boolean, val noKeyboard: Boolean)

/** 스펙 6.9: 차량 상태와 플랫폼 중 더 제한적인 쪽. 판정 불가 = BACKGROUND, CarUx 연결 실패(null) = PEEK. */
fun usageTierOf(motion: VehicleMotion?, carUx: CarUxState?): UsageTier {
    val fromVehicle =
        when (motion) {
            VehicleMotion.PARKED -> UsageTier.FULL
            VehicleMotion.STOPPED -> UsageTier.PEEK
            VehicleMotion.MOVING, null -> UsageTier.BACKGROUND
        }
    val fromPlatform = if (carUx == null || carUx.requiresDistractionOptimization) UsageTier.PEEK else UsageTier.FULL
    return minOf(fromVehicle, fromPlatform)
}
