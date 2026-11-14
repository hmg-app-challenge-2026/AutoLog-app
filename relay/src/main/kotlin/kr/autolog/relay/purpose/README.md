# `kr.autolog.relay.purpose`

회사 목적과 출발→도착 목적 규칙 (F-08, F-55). 바뀔 때마다 회사의 모든 차에 inbox PURPOSES(전체)를 보내고, 새 차가 등록되면 그 차에도 보낸다.

| API | 설명 |
|---|---|
| `GET/POST /admin/purposes`, `DELETE /admin/purposes/{id}` | 목적 목록(기본 목적 + 회사 추가). 추가는 이름(1~30자) + 구분(BUSINESS·COMMUTE·NON_BUSINESS). 지우면 그 목적을 쓰는 규칙도 지운다 |
| `GET/POST /admin/purpose-rules`, `DELETE /admin/purpose-rules/{id}` | 출발 장소(null = 어디서든) → 도착 장소 = 목적. 같은 출발·도착은 409 |

- 목적의 차 쪽 이름은 `custom:<구분>:<이름>`(차 `PurposeDetail.custom`과 같은 규칙). 규칙의 `purpose`는 기본 목적 이름이나 이 이름이다.
- 장소를 지우면 FK로 규칙도 지워지고, `AdminReferenceService.deletePlace`가 다시 배포한다.
