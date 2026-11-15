package kr.htaxlog.relay.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.htaxlog.relay.common.RelayProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

data class GeoPoint(val lat: Double, val lng: Double)

/** 주소 → 좌표 (F-52 운영 웹 장소 등록). 못 찾거나 꺼져 있으면 null. */
fun interface Geocoder {
    fun geocode(address: String): GeoPoint?
}

/**
 * 카카오 로컬 API 주소 검색. 키(KAKAO_REST_KEY)는 서버 환경변수에만 둔다.
 * 키가 비어 있으면 지오코딩을 하지 않는다 → 관리자가 좌표를 직접 넣어야 한다.
 */
@Component
class KakaoGeocoder(private val props: RelayProperties, builder: RestClient.Builder) : Geocoder {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rest = builder.baseUrl(props.geocoder.baseUrl).build()
    private val json = Json { ignoreUnknownKeys = true }

    override fun geocode(address: String): GeoPoint? {
        if (props.geocoder.kakaoKey.isBlank() || address.isBlank()) return null
        return try {
            val body =
                rest
                    .get()
                    .uri { it.path("/v2/local/search/address.json").queryParam("query", address).build() }
                    .header("Authorization", "KakaoAK ${props.geocoder.kakaoKey}")
                    .retrieve()
                    .body(String::class.java) ?: return null
            val doc =
                json
                    .parseToJsonElement(body)
                    .jsonObject["documents"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject ?: return null
            // 카카오는 x = 경도, y = 위도 (문자열)
            val lng = doc["x"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: doc["x"]?.jsonPrimitive?.doubleOrNull
            val lat = doc["y"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: doc["y"]?.jsonPrimitive?.doubleOrNull
            if (lat == null || lng == null) null else GeoPoint(lat, lng)
        } catch (e: RestClientException) {
            log.warn("지오코딩 실패: {}", e.message)
            null
        } catch (e: IllegalArgumentException) {
            log.warn("지오코딩 응답 형식 오류: {}", e.message)
            null
        }
    }
}
