package kr.autolog.relay.geo

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.shared.ReverseGeocodeResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.math.roundToLong

@Configuration
class GeoConfig {
    /** 차는 3초 남짓만 기다리고 내비 주소로 넘어간다. 이 타임아웃은 카카오 역지오코딩에만 건다(Gemini OCR은 오래 걸린다). */
    @Bean
    fun reverseGeocoder(props: RelayProperties, builder: RestClient.Builder): ReverseGeocoder = KakaoReverseGeocoder(
        props,
        builder.requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(2))
                setReadTimeout(Duration.ofSeconds(3))
            },
        ),
    )
}

/**
 * 운행 출발·도착 좌표 → 주소 (F-06, F-07). **좌표를 DB에 저장하지 않는다** — 차 → 서버 운행 데이터 업로드 금지(CONVENTIONS 7.1-3),
 * 조회만 한다. 같은 곳(소수 4자리 ≈ 11 m)은 메모리 캐시로 카카오를 다시 부르지 않는다(재시작하면 비워진다).
 */
@Service
class GeoService(private val geocoder: ReverseGeocoder) {
    private val cache =
        object : LinkedHashMap<Pair<Long, Long>, ReverseGeocodeResponse>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Long>, ReverseGeocodeResponse>?) =
                size > CACHE_SIZE
        }

    fun reverse(lat: Double, lng: Double): ReverseGeocodeResponse {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_COORDINATE", "좌표 범위가 잘못되었습니다")
        }
        val key = (lat * GRID).roundToLong() to (lng * GRID).roundToLong()
        synchronized(cache) { cache[key] }?.let { return it }
        val result =
            when (val r = geocoder.reverse(lat, lng)) {
                is ReverseLookup.Found -> r.response
                ReverseLookup.NotFound -> ReverseGeocodeResponse()
                ReverseLookup.Disabled ->
                    throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODER_DISABLED", "주소 변환이 꺼져 있습니다")
                ReverseLookup.Failed ->
                    throw ApiException(HttpStatus.BAD_GATEWAY, "GEOCODER_UNAVAILABLE", "주소 변환 서비스에 연결하지 못했습니다")
            }
        synchronized(cache) { cache[key] = result }
        return result
    }

    companion object {
        const val CACHE_SIZE = 500
        private const val GRID = 10_000.0
    }
}
