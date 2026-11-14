package kr.autolog.relay.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.shared.PlaceCandidateDto
import kr.autolog.shared.PlaceSearchResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * 장소 검색 (F-08 목적 규칙의 새 장소). 카카오 로컬 **키워드 검색**(장소 이름)을 먼저, 없으면 **주소 검색**.
 * 키는 `autolog.geocoder.kakao-key`. 검색어·결과는 저장하지 않는다. 로그에는 검색어를 남기지 않는다.
 */
@Service
class PlaceSearch(private val props: RelayProperties, builder: RestClient.Builder) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }
    private val rest = builder
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(2))
                setReadTimeout(Duration.ofSeconds(4))
            },
        ).baseUrl(props.geocoder.baseUrl)
        .build()

    fun search(raw: String): PlaceSearchResponse {
        val q = raw.trim()
        if (q.length !in
            2..MAX_QUERY
        ) {
            throw ApiException(HttpStatus.BAD_REQUEST, "SEARCH_INVALID", "검색어를 2~${MAX_QUERY}자로 입력해 주세요")
        }
        if (props.geocoder.kakaoKey.isBlank()) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODER_DISABLED", "장소 검색이 꺼져 있습니다")
        }
        return try {
            val keyword = documents("/v2/local/search/keyword.json", q).map { it.keywordCandidate() }
            val results = keyword.ifEmpty {
                documents("/v2/local/search/address.json", q).map { it.addressCandidate() }
            }
            PlaceSearchResponse(results.filterNotNull().take(LIMIT))
        } catch (e: RestClientException) {
            log.warn("장소 검색 실패: {}", e.message)
            throw ApiException(HttpStatus.BAD_GATEWAY, "GEOCODER_UNAVAILABLE", "장소 검색 서비스에 연결하지 못했습니다")
        }
    }

    private fun documents(path: String, q: String): List<JsonObject> {
        val body = rest.get()
            .uri { it.path(path).queryParam("query", q).queryParam("size", LIMIT).build() }
            .header("Authorization", "KakaoAK ${props.geocoder.kakaoKey}")
            .retrieve()
            .body(String::class.java) ?: return emptyList()
        return json.parseToJsonElement(body).jsonObject["documents"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.point(): Pair<Double, Double>? {
        // 카카오는 x = 경도, y = 위도 (문자열)
        val lng = str("x")?.toDoubleOrNull() ?: return null
        val lat = str("y")?.toDoubleOrNull() ?: return null
        return lat to lng
    }

    private fun JsonObject.keywordCandidate(): PlaceCandidateDto? {
        val (lat, lng) = point() ?: return null
        val name = str("place_name") ?: return null
        return PlaceCandidateDto(name, str("road_address_name") ?: str("address_name"), lat, lng)
    }

    private fun JsonObject.addressCandidate(): PlaceCandidateDto? {
        val (lat, lng) = point() ?: return null
        val road = this["road_address"] as? JsonObject
        val address = road?.str("address_name") ?: str("address_name") ?: return null
        return PlaceCandidateDto(address, address, lat, lng)
    }

    private companion object {
        const val LIMIT = 10
        const val MAX_QUERY = 60
    }
}
