package kr.autolog.vehicle.session

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootScreenPolicyTest {
    @Test
    fun `실제 부팅에서 화면 실행과 화면 위 표시 권한이 모두 있어야 화면을 요청한다`() {
        assertTrue(shouldLaunchScreenOnBoot(Intent.ACTION_BOOT_COMPLETED, StartupMode.SCREEN, true))
        assertFalse(shouldLaunchScreenOnBoot(Intent.ACTION_BOOT_COMPLETED, StartupMode.SCREEN, false))
        assertFalse(shouldLaunchScreenOnBoot(Intent.ACTION_BOOT_COMPLETED, StartupMode.BACKGROUND, true))
        assertFalse(shouldLaunchScreenOnBoot(Intent.ACTION_MY_PACKAGE_REPLACED, StartupMode.SCREEN, true))
    }
}
