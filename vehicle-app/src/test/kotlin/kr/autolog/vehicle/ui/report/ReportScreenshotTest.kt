package kr.autolog.vehicle.ui.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.core.MailError
import kr.autolog.vehicle.screenshot.captureAll
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReportScreenshotTest {
    private val ready = ReportUiState(
        month = YearMonth.of(2026, 11),
        summary = ReportSummaryUi(6, "127.0", 71, 2),
        recipients = listOf(
            RecipientUi("accounting@example.com", "회계팀", fromServer = true, selected = true),
            RecipientUi("me@example.com", "나", fromServer = false, selected = true),
        ),
        serverUpload = ServerUploadUi("10:24"),
    )

    @Test
    fun ready() = captureAll("report") { ReportScreen(ready, onAction = {}) }

    @Test
    fun built() = captureAll("report_built") {
        ReportScreen(ready.copy(step = ReportStep.Built), onAction = {})
    }

    @Test
    fun noMail() = captureAll("report_no_mail") {
        ReportScreen(ready.copy(step = ReportStep.Built, smtpConfigured = false), onAction = {})
    }

    @Test
    fun failed() = captureAll("report_failed") {
        ReportScreen(ready.copy(step = ReportStep.SendFailed(MailError.NETWORK)), onAction = {})
    }

    @Test
    fun sent() = captureAll("report_sent") {
        ReportScreen(ready.copy(step = ReportStep.Sent(2)), onAction = {})
    }
}
