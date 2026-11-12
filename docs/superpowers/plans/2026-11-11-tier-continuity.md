# 단계 이어짐 (D-31) — 계획과 구현 결과

> 브랜치 `feat/tier-continuity`(← `fix/emulator-qa`). 스펙 1.20 · 디자인 언어 1.52.

## 왜
에뮬레이터 QA(2026-11-11): "주행 시작하면 UI가 확 바뀌어서 괴리감이 심하다." 사용 단계마다 다른 화면을 통째로 그렸다.

| 단계 | 예전 | 문제 |
|---|---|---|
| 주차 | Home(좌우 두 섹션, 큰 글자, 타일 4개) | — |
| 정차 | Peek 패널(가운데 좁은 기둥, 작은 글자, 상태 줄에 경과 시간 없음, 타일 없음) | 배치·크기·정보가 달라 다른 앱처럼 보임 |
| 주행 | 빈 화면 + `moveTaskToBack` | 물러나기 전 빈 화면이 번쩍임 |
| 전환 | 단계 바뀔 때 화면 전체 페이드(`tierFadeIn/Out`) | 화면이 갈아 끼워지는 느낌 |

## 결정 (사용자, 2026-11-11)
1. 정차·주행 중 타일 4개: **그대로 흐리게** — 단 **[기록 종료]는 눌린다**(나머지만 비활성).
2. 주행: 지금처럼 물러나되 **빈 화면 대신 같은 화면**.
3. 정차 중 운행이 없으면: **대기 Home 읽기 전용**.

기각: 타일 숨김·안내 한 줄(배치가 바뀜), 주행 중 화면 유지(Pleos 운전자 주의 정책 위험), 정차 전용 패널 유지(괴리감).

## 구현
- `AutoLogRoot`: 단계와 무관하게 `FullApp` 하나. 단계는 잠금만 바꾼다 — `LocalInputLocked`(정차·주행 또는 FULL 진입 직후 300 ms) + 새 `LocalDrivingLocked`(정차·주행). 주행이면 같은 화면을 그린 채 `onRequestBackground`.
- `tappable(whileDriving = true)`: 정차·주행 잠금을 건너뛰고 진입 직후 잠금만 따른다. 지금은 [기록 종료] 타일 하나(`HomeDock` `DockItem.whileDriving`).
- `DockTile`: 잠긴 동안 아이콘·이름 `textTertiary`(흐림), `whileDriving` 타일은 그대로.
- `Navigator.showWhenParked(route?)`: 주차면 바로 쌓고(맨 위면 그대로), 아니면 다음 주차 때 Home 위에 연다(딥링크가 우선). null이면 기다리던 것을 지운다. `HomeViewModel`이 운행 결과를 이것으로 연다 — 정차 중 [기록 종료]로 끝난 운행의 결과가 주차 때 열린다. 그 전에 기록이 다시 이어지면(차속 > 3 km/h → 앱 A `resume`) 지운다.
- Home: 정차·주행 중엔 결정 팝업(복구·놓친 운행)을 띄우지 않는다(주차하면 묻는다). 종료 대기면 상태 줄 보조가 "2분 41초 후 기록 종료"(`HomeUiState.autoEndInSec` — 예전 Peek ⑤ 줄).
- `FullApp`: 운전자 배지는 주차한 대기 Home에만.
- 삭제(죽은 코드): `ui/peek/` 전체 · `FitOrDrop` · `Size.peekMaxWidth` · `MotionTokens.tierFadeIn/Out` · 문자열 `peek_*`·`section_odometer`(`peek_no_destination`은 `route_no_destination`으로 이름만).

## 테스트
- `AutoLogRootTest`: 정차 대기 = 같은 Home·눌리는 것 0·배지 없음 / 정차 기록 중 = [기록 종료]만 눌림 → 종료 대기, 결과 카드는 안 열림 / 주행 = 같은 Home을 그린 채 물러남.
- `NavigatorTest`: 정차 중 결과 → 다음 주차 때 / 그 전에 지우면 안 열림 / 주차 중 두 번 안 쌓임.
- `HomeReducerTest`: 종료 대기 남은 초.
- 스크린샷: `home_recording_stopped`·`home_ready_stopped`. 모션 프레임 `tier_full_to_stopped`.

## 남은 것 · 사각지대
- **Pleos 정책**: 정차 중 흐린 타일·[기록 종료] 버튼이 운전자 주의 분산 기준에 맞는지 맥 에뮬레이터·Pleos 가이드로 확인(V-17과 함께). 정보량은 예전 Peek보다 조금 많다.
- 최근 운행 줄(›)은 정차 중 눌리지 않지만 흐려지지 않는다 — 눌러 보면 반응이 없다. 필요하면 `TripListRow`도 잠금 모양을 받게.
- 정차 중 [기록 종료] 뒤 5초 안에 차가 움직이면 앱 A가 기록을 이어 간다(기어 P 대기만 예외) — 의도인지 앱 A와 확인.
- `UsageTier.PEEK` 이름은 그대로 둔다(뜻: 정차·읽기 전용). 바꾸면 테스트·문서 이름만 바뀌는 큰 변경이라 보류.
