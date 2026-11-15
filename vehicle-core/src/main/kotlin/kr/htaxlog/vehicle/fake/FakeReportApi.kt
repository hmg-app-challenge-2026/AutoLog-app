package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.flow.first
import kr.htaxlog.vehicle.core.LocalStore
import kr.htaxlog.vehicle.core.ReportApi
import kr.htaxlog.vehicle.core.ReportBuildResult
import kr.htaxlog.vehicle.core.ReportFile
import java.io.File
import java.time.YearMonth

/**
 * [ReportApi] 가짜 (프론트 초안에서 옮김). 빈 임시 파일과 [LocalStore]에서 계산한 요약을 돌려준다.
 * 요약 규칙은 실제와 같다: 거리 미확정은 합계에서 빼고, 목적·운전자·거리 중 하나라도 비면 검토 1건.
 * [nextFailure]를 넣으면 실패를 흉내 낸다.
 */
class FakeReportApi(
    private val store: LocalStore,
    private val plateNo: String = "12가3456",
    var nextFailure: String? = null,
) : ReportApi {
    override suspend fun build(month: YearMonth): ReportBuildResult {
        nextFailure?.let { return ReportBuildResult.Failed(it) }
        val rows = store.trips(month).first()
        val file = File.createTempFile("htaxlog-$month-", ".xlsx").apply { deleteOnExit() }
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
