package kr.autolog.relay.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kr.autolog.relay.common.RelayProperties
import kr.autolog.shared.ReverseGeocodeResponse
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class KakaoReverseGeocoderTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val geocoder =
        KakaoReverseGeocoder(
            RelayProperties(geocoder = RelayProperties.GeocoderProps(kakaoKey = "k", baseUrl = "https://kakao.test")),
            builder,
        )

    private fun doc(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun road(
        r1: String,
        r2: String,
        r3: String,
        roadName: String,
        main: String,
        sub: String = "",
        building: String = "",
    ) = """
        "road_address": {"address_name": "x", "region_1depth_name": "$r1", "region_2depth_name": "$r2",
          "region_3depth_name": "$r3", "road_name": "$roadName", "underground_yn": "N",
          "main_building_no": "$main", "sub_building_no": "$sub", "building_name": "$building", "zone_no": "00000"}
    """.trimIndent()

    @Test
    fun `서울 동 지역은 광역 기초 동 도로명 번호로 쓴다`() {
        val d = doc("""{${road("서울", "서대문구", "연희동", "연희로", "11", "1", "연희빌딩")}, "address": null}""")

        assertEquals(ReverseGeocodeResponse("서울 서대문구 연희동 연희로 11-1", "연희빌딩"), formatKakaoDocument(d))
    }

    @Test
    fun `특례시 일반구도 그대로 들어가고 부번 0과 빈 건물명은 뺀다`() {
        val d = doc("""{${road("경기", "성남시 분당구", "정자동", "정자일로", "1", "0")}}""")

        assertEquals(ReverseGeocodeResponse("경기 성남시 분당구 정자동 정자일로 1", null), formatKakaoDocument(d))
    }

    @Test
    fun `군 읍 지역`() {
        val d = doc("""{${road("부산", "기장군", "장안읍", "해맞이로", "10", "1")}}""")

        assertEquals("부산 기장군 장안읍 해맞이로 10-1", formatKakaoDocument(d)?.address)
    }

    @Test
    fun `도로명주소에 읍면동이 비면 지번 주소의 첫 단어를 쓴다`() {
        val d =
            doc(
                """{${road("경기", "안성시", "", "죽산초교길", "69", "4")},
                "address": {"address_name": "경기 안성시 죽산면 죽산리 343-1", "region_3depth_name": "죽산면 죽산리"}}""",
            )

        assertEquals("경기 안성시 죽산면 죽산초교길 69-4", formatKakaoDocument(d)?.address)
    }

    @Test
    fun `세종처럼 기초자치단체가 없으면 건너뛴다`() {
        val d = doc("""{${road("세종특별자치시", "", "어진동", "한누리대로", "2130")}}""")

        assertEquals("세종 어진동 한누리대로 2130", formatKakaoDocument(d)?.address)
    }

    @Test
    fun `도로명주소가 없으면 지번 주소를 줄여 쓴다`() {
        val d = doc("""{"road_address": null, "address": {"address_name": "강원특별자치도 평창군 대관령면 횡계리 1"}}""")

        assertEquals(ReverseGeocodeResponse("강원 평창군 대관령면 횡계리 1", null), formatKakaoDocument(d))
    }

    @Test
    fun `x는 경도 y는 위도로 묻고 KakaoAK 키를 붙인다`() {
        server
            .expect(requestTo(startsWith("https://kakao.test/v2/local/geo/coord2address.json")))
            .andExpect(queryParam("x", "127.1108"))
            .andExpect(queryParam("y", "37.402"))
            .andExpect(header("Authorization", "KakaoAK k"))
            .andRespond(
                withSuccess(
                    """{"meta":{"total_count":1},"documents":[{${road("경기", "성남시 분당구", "삼평동", "판교역로", "166")}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val r = geocoder.reverse(37.402, 127.1108)

        assertEquals(ReverseLookup.Found(ReverseGeocodeResponse("경기 성남시 분당구 삼평동 판교역로 166", null)), r)
        server.verify()
    }

    @Test
    fun `결과가 없으면 NotFound, 서버 오류면 Failed`() {
        server.expect(
            queryParam("y", "1.0"),
        ).andRespond(withSuccess("""{"documents":[]}""", MediaType.APPLICATION_JSON))
        server.expect(queryParam("y", "2.0")).andRespond(withServerError())

        assertEquals(ReverseLookup.NotFound, geocoder.reverse(1.0, 1.0))
        assertEquals(ReverseLookup.Failed, geocoder.reverse(2.0, 2.0))
    }

    @Test
    fun `키가 없으면 호출하지 않고 Disabled`() {
        val off = KakaoReverseGeocoder(RelayProperties(), RestClient.builder())

        assertEquals(ReverseLookup.Disabled, off.reverse(37.4, 127.1))
    }

    @Test
    fun `도로명도 지번도 없는 문서는 null`() {
        assertNull(formatKakaoDocument(doc("""{"road_address": null, "address": null}""")))
    }
}
