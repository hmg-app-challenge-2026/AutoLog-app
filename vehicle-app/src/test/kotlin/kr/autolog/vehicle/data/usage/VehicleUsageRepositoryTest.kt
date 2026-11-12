package kr.autolog.vehicle.data.usage

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.core.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleUsageRepositoryTest {
    @Test
    fun `차량 상태가 바뀌면 단계가 따라 바뀐다`() = runTest {
        val motion = MutableStateFlow<VehicleMotion?>(VehicleMotion.PARKED)
        val carUx = StaticCarUxSource()
        val repo = DefaultVehicleUsageRepository(motion, carUx.state, backgroundScope)

        repo.tier.test {
            assertEquals(UsageTier.FULL, awaitItem())
            motion.value = VehicleMotion.STOPPED
            assertEquals(UsageTier.PEEK, awaitItem())
            motion.value = VehicleMotion.MOVING
            assertEquals(UsageTier.BACKGROUND, awaitItem())
        }
    }

    @Test
    fun `플랫폼이 키보드를 막으면 noKeyboard가 켜지고 연결 실패도 막힌 것으로 본다`() = runTest {
        val carUx = StaticCarUxSource(CarUxState(false, noKeyboard = true))
        val repo =
            DefaultVehicleUsageRepository(MutableStateFlow(VehicleMotion.PARKED), carUx.state, backgroundScope)

        repo.noKeyboard.test {
            assertEquals(true, awaitItem())
            carUx.set(CarUxState(false, noKeyboard = false))
            assertEquals(false, awaitItem())
            carUx.set(null)
            assertEquals(true, awaitItem())
        }
    }
}
