# `kr.autolog.relay.geo`

운행 출발·도착 좌표 → 주소 (F-06, F-07, open-issues I-05). Pleos 내비 SDK에는 좌표 → 주소 API가 없어서 서버가 카카오에 대신 묻는다.

| API | 인증 | 응답 |
|---|---|---|
| `GET /v1/geo/reverse?lat=&lng=` | 기기 토큰(`Authorization: Bearer`) | `ReverseGeocodeResponse`(`shared`) — `{"address": "경기 성남시 분당구 정자동 정자일로 1", "place_name": "…"}` |
| `GET /v1/geo/search?q=` · `GET /admin/places/search?q=` | 기기 토큰 · 관리자 세션 | `PlaceSearchResponse` — 새 장소 후보 최대 10개(카카오 키워드 검색 → 없으면 주소 검색, `PlaceSearch`). 검색어 2~60자, 저장·로그 안 함. 타임아웃 connect 2 s / read 4 s |

- 주소 형식: `광역 기초 읍면동 도로명 번호`(`formatKakaoDocument`). 도로명주소가 없으면 지번 주소.
- 오류: 키 없음 503 `GEOCODER_DISABLED`, 카카오 실패 502 `GEOCODER_UNAVAILABLE`, 좌표 범위 오류 400 `INVALID_COORDINATE`, 토큰 오류 401. 주소를 모르면 200 + 빈 필드.
- **좌표는 저장하지 않는다**(조회만, CONVENTIONS 7.1-3). 같은 곳(소수 4자리)은 메모리 LRU 500개로 캐시. 로그 좌표는 소수 3자리까지.
- 키: `KAKAO_REST_KEY`(운영 웹 장소 지오코딩과 공용). 카카오 developers에서 REST API 키 발급 + 카카오맵 사용 설정 ON. 타임아웃 connect 2 s / read 3 s(`GeoConfig`).
- 차는 실패하면 내비 현재 위치 주소로 대신한다(`vehicle-core` `data/RelayGeocode`).
