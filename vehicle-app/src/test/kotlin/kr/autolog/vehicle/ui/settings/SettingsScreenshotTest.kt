package kr.autolog.vehicle.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.trips.TripDay
import kr.autolog.vehicle.ui.trips.TripWhen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsScreenshotTest {
    private val base = SettingsUiState(
        link = LinkUi.Linked("12가3456", "https://autolog.acme.co.kr", TripWhen(TripDay.TODAY, 11, 12, "10:14")),
        mailAccount = "autolog@acme.co.kr",
        recipients = listOf(
            RecipientRowUi("accounting@example.com", "회계팀", fromServer = true),
            RecipientRowUi("me@example.com", "나", fromServer = false),
        ),
        staff = listOf(
            StaffRowUi("u-1", "김영업", "영업1팀", fromServer = true),
            StaffRowUi("local-4", "최신입", "영업2팀", fromServer = false),
        ),
        app = AppInfo("0.1.0", demo = true),
    )

    /** 넓은 창: 목록 + 첫 섹션(차량 연결) / 좁은·세로 창: 목록만. */
    @Test
    fun list() = captureAll("settings") { SettingsScreen(base, onAction = {}) }

    @Test
    fun mail() = captureAll("settings_mail") {
        SettingsScreen(
            base.copy(
                section = SettingsSection.MAIL,
            ),
            onAction = {},
        )
    }

    @Test
    fun mailNotConnected() = captureAll("settings_mail_not_connected") {
        SettingsScreen(base.copy(section = SettingsSection.MAIL, mailAccount = null), onAction = {})
    }

    @Test
    fun recipients() = captureAll("settings_recipients") {
        SettingsScreen(base.copy(section = SettingsSection.RECIPIENTS), onAction = {})
    }

    /** 실제 앱의 차량 정보: 연결 카드 + 운행 기록 알약 권한 카드(권한 없음 · 이 차량에 권한 화면 없음 안내). */
    @Test
    fun vehicleOverlay() = captureAll("settings_vehicle_overlay") {
        SettingsScreen(
            base.copy(
                section = SettingsSection.VEHICLE,
                overlay = OverlayUi(allowed = false, settingsUnavailable = true),
            ),
            onAction = {},
        )
    }

    @Test
    fun register() = captureAll("settings_register") {
        SettingsScreen(
            base.copy(
                section = SettingsSection.VEHICLE,
                dialog = SettingsDialog.Register("7KQ3"),
            ),
            onAction = {},
        )
    }
}
