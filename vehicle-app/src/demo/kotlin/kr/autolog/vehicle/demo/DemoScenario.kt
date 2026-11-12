package kr.autolog.vehicle.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.MissedTrip
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.data.fake.FakeTripSession

/**
 * 스펙 6.7 데모 패널의 동작. VHAL 없이 시연 영상을 찍는다.
 * 주행 중에는 앱이 물러나므로 시나리오가 스스로 주차까지 진행해야 한다.
 */
class DemoScenario(private val trip: FakeTripSession, private val scope: CoroutineScope, private val clock: AppClock) {
    private var job: Job? = null

    /** 10초 주행(4 km) → 5초 신호 대기 → 10초 주행(5 km) → 주차. */
    fun playDrive() {
        job?.cancel()
        job =
            scope.launch {
                trip.setMotion(VehicleMotion.MOVING)
                repeat(10) {
                    delay(1_000)
                    trip.addKm(0.4)
                }
                trip.setMotion(VehicleMotion.STOPPED)
                delay(5_000)
                trip.setMotion(VehicleMotion.MOVING)
                repeat(10) {
                    delay(1_000)
                    trip.addKm(0.5)
                }
                trip.setMotion(VehicleMotion.PARKED)
            }
    }

    fun stop() = trip.setMotion(VehicleMotion.STOPPED)

    fun park() = trip.setMotion(VehicleMotion.PARKED)

    fun addKm(km: Double = 1.0) = trip.addKm(km)

    fun missedTrip() = trip.setMissedTrip(MissedTrip(15.0, clock.nowMs() - 7_200_000))

    fun recovery() = trip.enterRecovery(3.2)
}
