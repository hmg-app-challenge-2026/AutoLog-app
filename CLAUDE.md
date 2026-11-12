# AutoLog — AI 코딩 도구용 안내

이 저장소에서 코드를 쓰거나 고칠 때는 아래 컨벤션을 따른다.

@CONVENTIONS.md

- 2026-11-11 AutoLog-Frontend·AutoLog-Backend를 이 리포(AutoLog-app) 하나로 합쳤다. 화면 앱은 `vehicle-app/`, 로직은 `vehicle-core/`, 서버는 `relay/`, 웹은 `web/`.
- 명세는 `docs/`에 있다. 기능 ID(F-xx)는 `docs/AutoLog_기능명세.md` 3장 기준.
- 작업 전에 지금 사용자가 앱 A / 앱 B / 프론트 중 누구인지 확인하고, 그 사람의 소유 폴더(CONVENTIONS.md 1.2) 밖은 고치지 않는다(고쳐야 하면 소유자를 리뷰어로).
- 커밋 전 `./gradlew ktlintFormat` 후 CONVENTIONS.md 10장 명령(화면이면 11.5도)을 통과시킨다.

## 화면(`vehicle-app/`·`web/`)을 고칠 때

- 설계 기준: `docs/superpowers/specs/2026-11-09-autolog-frontend-design.md`. 결정(4장)을 바꾸려면 스펙을 먼저 고친다.
- 화면을 만들기 전에 `docs/design-language.md`를 읽는다. 색·글자·간격·모션은 토큰만 쓴다.
- 모든 화면의 전제: **주행 = 백그라운드(화면 없음) · 정차 = 같은 Home 읽기 전용(D-31) · 주차 = 전체 앱**(스펙 1.1), **같은 결정은 최소 조작 — 기본값은 자동 진행, 예외만 터치**(스펙 1.2, 조작 예산).
- 화면은 `vehicle-core`의 공개 계약만 쓴다: `vehicle-core/src/main/kotlin/kr/autolog/vehicle/core/Contracts.kt`(CONVENTIONS 1.3).
- 백엔드와 연결 상태·남은 문제: `docs/integration/backend-link.md`, `docs/integration/open-issues.md`.
- WSL에서는 Pleos 에뮬레이터가 돌지 않는다. 화면 확인은 Roborazzi 스크린샷(`recordRoborazziDemoDebug`)으로 하고, 실행 검증은 맥에서 한다.
