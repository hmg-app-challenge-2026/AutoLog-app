package kr.autolog.vehicle.ui.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.UploadMark
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.trips.TripDay
import kr.autolog.vehicle.ui.trips.TripRowUi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreenshotTest {
    private val rows = listOf(
        TripRowUi(
            "t-1", "본사", "판교 테크노밸리 거래처", "23.4", TripDay.TODAY, 11, 10, "09:12", null, "김영업",
            upload = UploadMark.PENDING,
        ),
        TripRowUi(
            "t-2", "판교 거래처", "수원 지점", "41.2", TripDay.YESTERDAY, 11, 9, "17:40", "지점 이동", "이현장",
            upload = UploadMark.DONE,
        ),
        TripRowUi("t-3", "본사", "강남 고객사", "8.6", TripDay.EARLIER, 11, 7, "14:05", "거래처 방문", null, driverUnset = true),
        TripRowUi("t-4", "본사", "본사", null, TripDay.EARLIER, 11, 5, "10:30", "외근", "박관리", upload = UploadMark.FAILED),
    )
    private val state = HistoryUiState(
        month = YearMonth.of(2026, 11),
        loading = false,
        canGoNext = false,
        tripCount = 4,
        totalKm = "73.2",
        rows = rows,
        driverChoices = listOf(DriverChoice("u-1", "김영업"), DriverChoice("u-2", "이현장"), DriverChoice("u-3", "박관리")),
    )

    @Test
    fun list() = captureAll("history") { HistoryScreen(state, onAction = {}) }

    @Test
    fun empty() = captureAll("history_empty") {
        HistoryScreen(
            state.copy(
                month = YearMonth.of(2026, 10),
                canGoNext = true,
                tripCount = 0,
                totalKm = "0.0",
                rows = emptyList(),
            ),
            onAction = {
            },
        )
    }
}
