package kr.autolog.vehicle.data.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.ConnectionState
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.RegisterResult
import kr.autolog.vehicle.core.ReportBuildResult
import kr.autolog.vehicle.data.upload.UploadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class FakeLocalStoreTest {
    private val now = LocalDateTime.of(2026, 11, 20, 10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
    private val clock = AppClock { now }

    @Test
    fun `월별 조회는 그 달에 시작한 운행만 최신 순으로`() = runTest {
        val store = FakeLocalStore(clock)

        val rows = store.trips(YearMonth.of(2026, 11)).first()

        assertEquals(6, rows.size)
        assertEquals(rows.sortedByDescending { it.startTime }, rows)
        assertTrue(store.trips(YearMonth.of(2026, 10)).first().isEmpty())
    }

    @Test
    fun `목적 미확정 운행만 확인 필요 목록에 있다`() = runTest {
        val store = FakeLocalStore(clock)

        val pending = store.pendingReviews.first()

        assertTrue(pending.isNotEmpty())
        assertTrue(pending.all { it.purpose == null })
    }

    @Test
    fun `지난 운행의 목적을 확정하면 확인 필요에서 빠진다`() = runTest {
        val store = FakeLocalStore(clock)
        val target = store.pendingReviews.first().first()

        store.confirm(target.tripId, PurposeDetail.CLIENT_VISIT, "미팅")

        assertEquals(PurposeDetail.CLIENT_VISIT, store.trip(target.tripId)?.purpose)
        assertTrue(store.pendingReviews.first().none { it.tripId == target.tripId })
    }

    @Test
    fun `영수증을 처리하면 inbox에서 빠진다`() = runTest {
        val store = FakeLocalStore(clock)
        val id = store.receiptInbox.first().first().receiptId

        store.ackReceipt(id)

        assertNull(store.receiptInbox.first().firstOrNull { it.receiptId == id })
    }

    @Test
    fun `운행기록부 요약은 거리 미확정을 빼고 합하고 미확정 항목을 검토로 센다`() = runTest {
        val store = FakeLocalStore(clock)
        val rows = store.trips(YearMonth.of(2026, 11)).first()

        val built = FakeReportApi(store, clock).build(YearMonth.of(2026, 11)) as ReportBuildResult.Built

        assertEquals(rows.size, built.file.tripCount)
        assertEquals(rows.mapNotNull { it.distanceKm }.sum(), built.file.totalKm, 0.001)
        assertEquals(
            rows.count {
                it.purpose == null || it.driverUserId == null || it.distanceKm == null
            },
            built.file.reviewCount,
        )
    }

    @Test
    fun `연결 코드는 서버와 같은 대문자·숫자 6자리여야 한다 — 0·1·I·O는 없다`() = runTest {
        val device = FakeDeviceApi(clock)

        assertTrue(device.register("https://autolog.acme.co.kr", "7KQ") is RegisterResult.Invalid)
        assertTrue(device.register("https://autolog.acme.co.kr", "7KQ3M0") is RegisterResult.Invalid)
        assertTrue(device.register("autolog.acme.co.kr", "7KQ3MZ") is RegisterResult.Invalid)
        assertEquals(RegisterResult.Ok, device.register("https://autolog.acme.co.kr", "7kq3mz"))
        assertTrue(device.connection.value is ConnectionState.Registered)
    }

    @Test
    fun `지금 전송하면 대기 건수가 0이 된다`() = runTest {
        val upload = FakeUploadApi(clock)
        val before = upload.status.value.pendingCount

        val result = upload.uploadNow()

        assertEquals(UploadResult.Uploaded(before), result)
        assertEquals(0, upload.status.value.pendingCount)
    }
}
