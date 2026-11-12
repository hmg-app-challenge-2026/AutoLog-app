package kr.autolog.vehicle.data.repository

import kr.autolog.vehicle.common.AppZone
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeDetail
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class TripResultRepositoryTest {
    private fun at(h: Int) = ZonedDateTime.of(2026, 11, 9, h, 0, 0, 0, AppZone).toInstant().toEpochMilli()

    @Test
    fun `분류를 안 바꾸면 이미 있던 세부 목적을 지킨다`() {
        assertEquals(
            PurposeDetail.MEETING,
            detailFor(PurposeCategory.BUSINESS, PurposeDetail.MEETING, PurposeDetail.CLIENT_VISIT, at(9)),
        )
    }

    @Test
    fun `엔진 추천이 같은 분류면 그 세부 목적, 아니면 분류의 대표값`() {
        assertEquals(
            PurposeDetail.CLIENT_VISIT,
            detailFor(PurposeCategory.BUSINESS, null, PurposeDetail.CLIENT_VISIT, at(9)),
        )
        assertEquals(
            PurposeDetail.FIELD_WORK,
            detailFor(PurposeCategory.BUSINESS, null, PurposeDetail.NEEDS_CHECK, at(9)),
        )
        assertEquals(
            PurposeDetail.PERSONAL,
            detailFor(PurposeCategory.NON_BUSINESS, PurposeDetail.CLIENT_VISIT, null, at(9)),
        )
    }

    @Test
    fun `출퇴근은 정오 전이면 출근, 이후면 퇴근`() {
        assertEquals(PurposeDetail.COMMUTE_IN, detailFor(PurposeCategory.COMMUTE, null, null, at(8)))
        assertEquals(PurposeDetail.COMMUTE_OUT, detailFor(PurposeCategory.COMMUTE, null, null, at(18)))
    }
}
