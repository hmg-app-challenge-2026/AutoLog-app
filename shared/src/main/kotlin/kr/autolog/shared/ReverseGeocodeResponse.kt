package kr.autolog.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /v1/geo/reverse?lat=&lng= 응답 (F-06, F-07). 운행 출발·도착 좌표 → 주소.
 * 서버는 좌표를 저장하지 않고 카카오에 물어본 결과만 돌려준다.
 */
@Serializable
data class ReverseGeocodeResponse(
    /** `광역 기초 읍면동 도로명 번호` 한 줄. 예: `경기 성남시 분당구 정자동 정자일로 1`. 못 찾으면 null. */
    val address: String? = null,
    /** 건물 이름(카카오 `building_name`). 없으면 null. */
    @SerialName("place_name") val placeName: String? = null,
)
