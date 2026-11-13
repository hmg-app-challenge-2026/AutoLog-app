package kr.autolog.relay.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.autolog.relay.common.RelayProperties
import kr.autolog.shared.KoreanAddress
import kr.autolog.shared.ReverseGeocodeResponse
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** 좌표 → 주소 조회 결과. 실패 이유에 따라 HTTP 상태가 다르다([GeoService]). */
sealed interface ReverseLookup {
    data class Found(val response: ReverseGeocodeResponse) : ReverseLookup

    /** 카카오가 주소를 모른다(바다·국외 등). 정상 응답이다. */
    data object NotFound : ReverseLookup

    /** 키(KAKAO_REST_KEY)가 없어 꺼져 있다. */
    data object Disabled : ReverseLookup

    /** 카카오 호출 실패(네트워크·4xx·5xx·형식 오류). */
    data object Failed : ReverseLookup
}

fun interface ReverseGeocoder {
    fun reverse(lat: Double, lng: Double): ReverseLookup
}

/**
 * 카카오 로컬 API 좌표 → 주소 변환(coord2address, F-06·F-07). 키는 운영 웹 지오코딩과 같은 `autolog.geocoder.kakao-key`.
 * 타임아웃은 [GeoConfig]가 정한다(차가 짧게 기다리고 내비 주소로 넘어가도록).
 */
class KakaoReverseGeocoder(private val props: RelayProperties, builder: RestClient.Builder) : ReverseGeocoder {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rest = builder.baseUrl(props.geocoder.baseUrl).build()
    private val json = Json { ignoreUnknownKeys = true }

    override fun reverse(lat: Double, lng: Double): ReverseLookup {
        if (props.geocoder.kakaoKey.isBlank()) return ReverseLookup.Disabled
        return try {
            val body =
                rest
                    .get()
                    // 카카오는 x = 경도, y = 위도
                    .uri {
                        it.path("/v2/local/geo/coord2address.json").queryParam("x", lng).queryParam("y", lat).build()
                    }
                    .header("Authorization", "KakaoAK ${props.geocoder.kakaoKey}")
                    .retrieve()
                    .body(String::class.java) ?: return ReverseLookup.Failed
            val doc =
                json
                    .parseToJsonElement(body)
                    .jsonObject["documents"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject ?: return ReverseLookup.NotFound
            formatKakaoDocument(doc)?.let { ReverseLookup.Found(it) } ?: ReverseLookup.NotFound
        } catch (e: RestClientException) {
            log.warn("역지오코딩 실패 ({}, {}): {}", "%.3f".format(lat), "%.3f".format(lng), e.message)
            ReverseLookup.Failed
        } catch (e: IllegalArgumentException) {
            log.warn("역지오코딩 응답 형식 오류: {}", e.message)
            ReverseLookup.Failed
        }
    }
}

/**
 * coord2address 문서 하나 → `광역 기초 읍면동 도로명 번호` (open-issues I-05). 순수 함수.
 * 예: `서울 서대문구 연희동 연희로 11-1`, `경기 성남시 분당구 정자동 정자일로 1`, `부산 기장군 장안읍 해맞이로 10-1`.
 * 도로명주소가 없으면 지번 주소(`경기 안성시 죽산면 죽산리 343-1`)를 쓴다. 둘 다 없으면 null.
 */
internal fun formatKakaoDocument(doc: JsonObject): ReverseGeocodeResponse? {
    val road = doc["road_address"].objectOrNull()
    val jibun = doc["address"].objectOrNull()
    val address =
        if (road != null) {
            // 도로명주소의 읍면동이 비면(일부 지역) 지번 주소의 읍면동(첫 단어, "죽산면 죽산리" → "죽산면")을 쓴다
            val town = road.text("region_3depth_name") ?: jibun?.text("region_3depth_name")?.substringBefore(' ')
            val main = road.text("main_building_no")
            val sub = road.text("sub_building_no")?.takeIf { it != "0" }
            val number = main?.let { if (sub != null) "$it-$sub" else it }
            KoreanAddress.join(
                KoreanAddress.shortenRegion(road.text("region_1depth_name")),
                road.text("region_2depth_name"),
                town,
                road.text("road_name"),
                number,
            ) ?: KoreanAddress.shortenRegion(road.text("address_name"))
        } else {
            KoreanAddress.shortenRegion(jibun?.text("address_name"))
        } ?: return null
    return ReverseGeocodeResponse(address = address, placeName = road?.text("building_name"))
}

private fun JsonElement?.objectOrNull(): JsonObject? = if (this == null || this is JsonNull) null else jsonObject

private fun JsonObject.text(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
