package kr.htaxlog.vehicle.report

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kr.htaxlog.vehicle.core.ReportBuildResult
import kr.htaxlog.vehicle.data.HTaxLogDb
import kr.htaxlog.vehicle.data.PurposeSource
import kr.htaxlog.vehicle.data.RoomReportSource
import kr.htaxlog.vehicle.data.TripEntity
import kr.htaxlog.vehicle.data.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.util.zip.ZipFile

/** Room → 운행기록부 파일 (에뮬레이터 내부 저장소). */
@RunWith(AndroidJUnit4::class)
class ReportOnDeviceTest {
    @Test
    fun buildsReportFromRoomInAppStorage(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HTaxLogDb::class.java).build()
        val start = Instant.parse("2026-11-03T00:00:00Z").toEpochMilli()
        db.tripDao().upsert(
            TripEntity(
                tripId = "t-1",
                vehicleId = "car-1",
                userId = null,
                startTime = start,
                endTime = start + 1_800_000,
                startOdometerKm = 18_420.5,
                endOdometerKm = 18_443.9,
                distanceKm = 23.4,
                purposeDetail = "CLIENT_VISIT",
                purposeSource = PurposeSource.USER_CONFIRMED,
                status = TripStatus.CONFIRMED,
            ),
        )
        val dir = File(context.filesDir, "reports")
        val source = RoomReportSource(db.tripDao(), db.referenceDao(), db.expenseDao()) { "car-1" }
        val builder = ReportBuilder(source, { dir }, { "12가3456" })

        val r = builder.build(YearMonth.of(2026, 11)) as ReportBuildResult.Built

        assertEquals(File(dir, "운행기록부_12가3456_202611.xlsx"), r.file.file)
        assertEquals(1, r.file.reviewCount)
        assertEquals("DRIVER_UNSET", db.tripDao().trip("t-1")!!.reviewFlags)
        ZipFile(r.file.file).use { assertTrue(it.getEntry("xl/worksheets/sheet4.xml") != null) }
        db.close()
    }
}
