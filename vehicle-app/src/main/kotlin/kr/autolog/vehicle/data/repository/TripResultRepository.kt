package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.AppZone
import kr.autolog.vehicle.core.InputMode
import kr.autolog.vehicle.core.LocalStore
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeEngine
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.TripFacts
import kr.autolog.vehicle.core.TripRow
import kr.autolog.vehicle.core.TripSessionApi
import java.time.Instant

/**
 * 운행 결과 카드에 보일 한 운행(스펙 7.3, D-27). [category] = 저장된 분류, 아직 없으면 [DefaultPurposePolicy]의 기본값.
 * [classified] = 분류가 이미 저장돼 있는지(운행 기록 조회에서 다시 열었을 때 true).
 * [endsAt] = 종료 조건은 됐지만 아직 끝나지 않은 운행(앱 A `StopCandidate`)의 종료 시각 — 그 전엔 [기록 이어가기]로 되돌릴 수 있다.
 * null이면 이미 끝나 저장된 운행. 끝나기 전 [endTime]은 지금 시각(경과 시간 표시용).
 */
data class TripResult(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val distanceKm: Double?,
    val origin: String?,
    val destination: String?,
    val category: PurposeCategory,
    val classified: Boolean,
    val endsAt: Long? = null,
)

/**
 * 카드에 처음 선택돼 있을 분류(D-27). **지금은 항상 일반 업무.**
 * 캘린더·배차 같은 일정 서비스와 연동해 출퇴근 등을 고르게 할 때는 이 인터페이스의 구현만 바꾼다 — 화면·저장 경로는 그대로.
 */
fun interface DefaultPurposePolicy {
    suspend fun defaultFor(trip: TripFacts): PurposeCategory
}

/** 지금 기본값(D-27): 모든 운행을 일반 업무로 시작한다. */
val BusinessByDefault = DefaultPurposePolicy { PurposeCategory.BUSINESS }

/** 운행 결과(스펙 7.3, F-08). 화면은 큰 분류 3가지만 고르고, 저장할 세부 목적은 여기 한곳에서 정한다([detailFor]). */
interface TripResultRepository {
    /**
     * 운행이 없으면 null. **계속 흐른다**: 종료 대기 중이면 세션의 지금 값([TripResult.endsAt] 있음),
     * 끝나 저장되면 저장소 값으로 바뀐다(같은 카드가 "끝나기 전 → 끝남"을 이어서 보여 준다).
     */
    fun result(tripId: String): Flow<TripResult?>

    /** [category]로 분류해 저장한다(다시 부르면 바꾼다). 아직 끝나지 않은 운행이면 아무것도 하지 않는다. */
    suspend fun classify(tripId: String, category: PurposeCategory)
}

class DefaultTripResultRepository(
    private val store: LocalStore,
    private val session: TripSessionApi,
    private val purpose: PurposeEngine,
    private val clock: AppClock,
    private val defaults: DefaultPurposePolicy = BusinessByDefault,
    private val vehicleId: () -> String,
) : TripResultRepository {
    override fun result(tripId: String): Flow<TripResult?> = session.state.map { s ->
        if (s is SessionState.StopCandidate && s.trip.tripId == tripId) s.toResult() else store.trip(tripId)?.toResult()
    }.distinctUntilChanged()

    override suspend fun classify(tripId: String, category: PurposeCategory) {
        val row = store.trip(tripId) ?: return
        val suggested = purpose.suggest(row.facts()).detail
        session.confirmPurpose(tripId, detailFor(category, row.purpose, suggested, row.startTime), null, InputMode.TAP)
    }

    /** 종료 대기 중인 운행 — 아직 저장소에 없다. 경과 시간은 지금까지. */
    private suspend fun SessionState.StopCandidate.toResult() = TripResult(
        tripId = trip.tripId,
        startTime = trip.startTime,
        endTime = clock.nowMs(),
        distanceKm = liveKm,
        origin = trip.originLabel,
        destination = trip.destinationLabel,
        category = defaults.defaultFor(
            TripFacts(
                tripId = trip.tripId,
                vehicleId = vehicleId(),
                startTime = trip.startTime,
                destinationLabel = trip.destinationLabel,
                driverUserId = trip.driverUserId,
            ),
        ),
        classified = false,
        endsAt = deadlineAt,
    )

    private suspend fun TripRow.toResult(): TripResult {
        val saved = purpose?.category?.takeIf { it != PurposeCategory.UNDETERMINED }
        return TripResult(
            tripId = tripId,
            startTime = startTime,
            endTime = endTime,
            distanceKm = distanceKm,
            origin = originLabel,
            destination = destinationLabel,
            category = saved ?: defaults.defaultFor(facts()),
            classified = saved != null,
        )
    }

    private fun TripRow.facts() = TripFacts(
        tripId = tripId,
        vehicleId = vehicleId(),
        startTime = startTime,
        endTime = endTime,
        destinationLabel = destinationLabel,
        driverUserId = driverUserId,
    )
}

/**
 * 큰 분류 → 저장할 세부 목적(D-27). 운행기록부가 나누는 것은 업무용·출퇴근용·비업무용이고, 세부 목적은 기록부 "목적" 칸에 남는 설명이다.
 * 1) 이미 같은 분류의 세부 목적이 있으면 그대로(고치러 왔다가 분류를 안 바꾼 경우)
 * 2) 엔진 추천이 같은 분류면 그 세부 목적(배차·거래처 규칙 — 예: 거래처 방문)
 * 3) 아니면 분류의 대표값: 일반 업무 = 외근, 출퇴근 = 정오 전 출근·이후 퇴근, 개인 용무 = 개인 이동
 */
internal fun detailFor(
    category: PurposeCategory,
    current: PurposeDetail?,
    suggested: PurposeDetail?,
    startTime: Long,
): PurposeDetail = when {
    current?.category == category -> current
    suggested?.category == category -> suggested
    category == PurposeCategory.COMMUTE ->
        if (Instant.ofEpochMilli(startTime).atZone(AppZone).hour <
            NOON
        ) {
            PurposeDetail.COMMUTE_IN
        } else {
            PurposeDetail.COMMUTE_OUT
        }
    category == PurposeCategory.NON_BUSINESS -> PurposeDetail.PERSONAL
    else -> PurposeDetail.FIELD_WORK
}

private const val NOON = 12
