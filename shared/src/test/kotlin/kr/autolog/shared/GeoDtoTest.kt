package kr.autolog.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoDtoTest {
    @Test
    fun `역지오코딩 응답이 공용 Json으로 왕복한다`() {
        val original = ReverseGeocodeResponse("서울 서대문구 연희동 연희로 11-1", "연희빌딩")

        val text = AutoLogJson.encodeToString(ReverseGeocodeResponse.serializer(), original)

        assertEquals(original, AutoLogJson.decodeFromString(ReverseGeocodeResponse.serializer(), text))
    }

    @Test
    fun `못 찾은 응답은 빈 객체로도 읽힌다`() {
        val r = AutoLogJson.decodeFromString(ReverseGeocodeResponse.serializer(), "{}")

        assertNull(r.address)
        assertNull(r.placeName)
    }

    @Test
    fun `광역자치단체 정식 이름을 줄인다`() {
        assertEquals("서울 서대문구 연희동 연희로 11-1", KoreanAddress.shortenRegion("서울특별시 서대문구 연희동 연희로 11-1"))
        assertEquals("경기 성남시 분당구 정자일로 1", KoreanAddress.shortenRegion("경기도  성남시 분당구 정자일로 1"))
        assertEquals("부산 기장군 장안읍 해맞이로 1", KoreanAddress.shortenRegion("부산광역시 기장군 장안읍 해맞이로 1"))
        assertEquals("강원 춘천시 중앙로 1", KoreanAddress.shortenRegion("강원특별자치도 춘천시 중앙로 1"))
    }

    @Test
    fun `이미 짧은 이름이나 빈 주소는 그대로 둔다`() {
        assertEquals("서울 중구 세종대로 110", KoreanAddress.shortenRegion("서울 중구 세종대로 110"))
        assertNull(KoreanAddress.shortenRegion("   "))
        assertNull(KoreanAddress.shortenRegion(null))
    }

    @Test
    fun `빈 조각은 건너뛰고 잇는다`() {
        assertEquals("세종 한누리대로 2130", KoreanAddress.join("세종", "", null, "한누리대로", "2130"))
        assertNull(KoreanAddress.join("", null))
    }
}
