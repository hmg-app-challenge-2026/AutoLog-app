package kr.htaxlog.relay.admin

import kr.htaxlog.relay.common.RelayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URLEncoder

class KakaoGeocoderTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val geocoder =
        KakaoGeocoder(
            RelayProperties(geocoder = RelayProperties.GeocoderProps(kakaoKey = "k", baseUrl = "https://kakao.test")),
            builder,
        )

    @Test
    fun `주소 검색 첫 결과의 y·x를 위도·경도로 쓴다`() {
        server
            .expect(queryParam("query", URLEncoder.encode("판교역로 166", "UTF-8").replace("+", "%20")))
            .andExpect(header("Authorization", "KakaoAK k"))
            .andRespond(withSuccess("""{"documents":[{"x":"127.1108","y":"37.4020"}]}""", MediaType.APPLICATION_JSON))

        assertEquals(GeoPoint(37.4020, 127.1108), geocoder.geocode("판교역로 166"))
    }

    @Test
    fun `결과가 없거나 서버 오류면 null`() {
        server.expect(
            queryParam("query", "x"),
        ).andRespond(withSuccess("""{"documents":[]}""", MediaType.APPLICATION_JSON))
        server.expect(queryParam("query", "y")).andRespond(withServerError())

        assertNull(geocoder.geocode("x"))
        assertNull(geocoder.geocode("y"))
    }

    @Test
    fun `키가 없으면 호출하지 않는다`() {
        val off = KakaoGeocoder(RelayProperties(), RestClient.builder())

        assertNull(off.geocode("판교역로 166"))
    }
}
