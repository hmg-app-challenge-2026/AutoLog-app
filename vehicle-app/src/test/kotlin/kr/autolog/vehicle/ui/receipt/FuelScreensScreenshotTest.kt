package kr.autolog.vehicle.ui.receipt

import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.fake.FakeReceiptApi
import kr.autolog.vehicle.screenshot.captureAll
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FuelScreensScreenshotTest {
    @Test
    fun qr() = captureAll("fuel_qr") {
        ReceiptQrScreen(
            ReceiptQrUiState(
                loading = false,
                url = "https://autolog.example/r/demo",
                expiresAt = 600_000,
                now = 19_000,
            ),
            onAction = {},
        )
    }

    @Test
    fun manual() = captureAll("fuel_manual") {
        FuelEntryScreen(FuelEntryUiState(), onAction = {})
    }

    @Test
    fun ocr() = captureAll("fuel_ocr") {
        val receipt = FakeReceiptApi.SAMPLE_RECEIPT.copy(thumbUrl = "")
        FuelEntryScreen(reduceReceiptEntry(receipt, FakeReceiptApi.SAMPLE_CANDIDATES), onAction = {})
    }
}
