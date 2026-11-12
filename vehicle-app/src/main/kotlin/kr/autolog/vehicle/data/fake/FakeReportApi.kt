package kr.autolog.vehicle.data.fake

import kotlinx.coroutines.flow.first
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.LocalStore
import kr.autolog.vehicle.core.ReportApi
import kr.autolog.vehicle.core.ReportBuildResult
import kr.autolog.vehicle.core.ReportFile
import java.io.File
import java.time.YearMonth

/**
 * 앱 A ReportBuilder(XLSX)가 올 때까지 쓰는 가짜. 빈 임시 파일과 [LocalStore]에서 계산한 요약을 돌려준다.
 * 요약 규칙은 실제와 같다: 거리 미확정은 합계에서 빼고, 목적·운전자·거리 중 하나라도 비면 검토 1건.
 */
class FakeReportApi(
    private val store: LocalStore,
    private val clock: AppClock,
    private val plateNo: String = "12가3456",
) : ReportApi {
    override suspend fun build(month: YearMonth): ReportBuildResult {
        val rows = store.trips(month).first()
        val file = File.createTempFile("autolog-$month-${clock.nowMs()}", ".xlsx").apply { deleteOnExit() }
        return ReportBuildResult.Built(
            ReportFile(
                month = month,
                file = file,
                plateNo = plateNo,
                tripCount = rows.size,
                totalKm = rows.mapNotNull { it.distanceKm }.sum(),
                reviewCount = rows.count { it.purpose == null || it.driverUserId == null || it.distanceKm == null },
            ),
        )
    }
}
