package kr.htaxlog.vehicle.speech

import kr.htaxlog.shared.PlaceKind
import kr.htaxlog.vehicle.core.PlaceInfo
import kr.htaxlog.vehicle.core.PurposeDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurposeVoiceParserTest {
    private val places =
        listOf(
            PlaceInfo("p1", "한빛전자", PlaceKind.CLIENT, 0.0, 0.0),
            PlaceInfo("p2", "수원 지점", PlaceKind.BRANCH, 0.0, 0.0),
            PlaceInfo("p3", "물류센터", PlaceKind.OFFICE, 0.0, 0.0, defaultPurpose = "FIELD_WORK"),
            PlaceInfo("home", "우리집", PlaceKind.HOME, 0.0, 0.0),
        )

    private fun detail(s: String) = PurposeVoiceParser.parse(s, places)?.detail

    @Test
    fun `명세 예시 판교 A고객사 미팅은 거래처 방문이고 원문을 note로 남긴다`() {
        val r = PurposeVoiceParser.parse("판교 A고객사 미팅", places)!!

        assertEquals(PurposeDetail.CLIENT_VISIT, r.detail)
        assertEquals("판교 A고객사 미팅", r.note)
    }

    @Test
    fun `출퇴근·개인·출장 키워드가 가장 먼저`() {
        assertEquals(PurposeDetail.COMMUTE_IN, detail("출근합니다"))
        assertEquals(PurposeDetail.COMMUTE_OUT, detail("퇴근"))
        assertEquals(PurposeDetail.PERSONAL, detail("개인 용무"))
        assertEquals(PurposeDetail.BUSINESS_TRIP, detail("부산 출장 갑니다"))
    }

    @Test
    fun `등록 장소 이름을 말하면 그 장소 종류·기본 목적으로`() {
        assertEquals(PurposeDetail.CLIENT_VISIT, detail("한빛 전자 가는 길"))
        assertEquals(PurposeDetail.BRANCH_MOVE, detail("수원지점"))
        assertEquals(PurposeDetail.FIELD_WORK, detail("물류센터 다녀옴"))
    }

    @Test
    fun `장소가 없으면 키워드로`() {
        assertEquals(PurposeDetail.MEETING, detail("본사 회의"))
        assertEquals(PurposeDetail.FIELD_WORK, detail("현장 점검"))
    }

    @Test
    fun `아무것도 안 맞으면 null`() {
        assertNull(detail("음 그러니까"))
        assertNull(detail("  "))
    }

    @Test
    fun `자택 이름은 장소 매칭에 쓰지 않는다`() {
        assertNull(detail("우리집"))
    }
}
