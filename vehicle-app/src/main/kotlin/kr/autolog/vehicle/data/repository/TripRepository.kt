package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.core.LocalStore
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.TripRow
import kr.autolog.vehicle.core.UploadState
import java.time.YearMonth

/** 이력·최근 운행·월 요약(스펙 7.2·7.7·7.8). */
interface TripRepository {
    fun history(month: YearMonth): Flow<List<HistoryItem>>

    /** 달과 상관없이 가장 최근 운행 [limit]건, 최신 순(Home "최근 운행 기록", 스펙 7.2). */
    fun recent(limit: Int): Flow<List<HistoryItem>>

    fun summary(month: YearMonth): Flow<MonthSummary>
}

class DefaultTripRepository(private val store: LocalStore, private val settings: SettingsApi) : TripRepository {
    override fun history(month: YearMonth): Flow<List<HistoryItem>> = items(store.trips(month))

    override fun recent(limit: Int): Flow<List<HistoryItem>> = items(store.recent(limit))

    /** 저장 행 → 화면 항목. 운전자 이름은 직원 목록에서 찾는다(이력·최근 운행이 같은 변환을 쓴다). */
    private fun items(rows: Flow<List<TripRow>>): Flow<List<HistoryItem>> =
        combine(rows, settings.users()) { list, users ->
            val names = users.associate { it.userId to it.name }
            list.map { r ->
                HistoryItem(
                    tripId = r.tripId,
                    startTime = r.startTime,
                    endTime = r.endTime,
                    driverName = r.driverUserId?.let { names[it] },
                    driverUnset = r.driverUserId == null,
                    // 목록은 한 줄 — 이름(등록 장소·건물), 없으면 주소(I-05)
                    origin = r.originLabel ?: r.originAddress,
                    destination = r.destinationLabel ?: r.destinationAddress,
                    distanceKm = r.distanceKm,
                    purpose = r.purpose,
                    upload =
                    when (r.upload) {
                        UploadState.PENDING -> UploadMark.PENDING
                        UploadState.DONE -> UploadMark.DONE
                        UploadState.FAILED -> UploadMark.FAILED
                        null -> null
                    },
                )
            }
        }

    override fun summary(month: YearMonth): Flow<MonthSummary> = store.trips(month).map { rows ->
        MonthSummary(
            tripCount = rows.size,
            totalKm = rows.mapNotNull { it.distanceKm }.sum(),
            businessKm = rows.filter {
                it.purpose?.category == PurposeCategory.BUSINESS
            }.mapNotNull { it.distanceKm }.sum(),
            reviewCount = rows.count { it.purpose == null || it.driverUserId == null || it.distanceKm == null },
        )
    }
}
