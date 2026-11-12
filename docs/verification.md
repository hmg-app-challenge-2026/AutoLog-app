# 검증 기록

스펙 13장 V-xx 결과. 날짜·환경(WSL/맥 에뮬레이터/실차)을 꼭 적는다.

| ID | 결과 | 환경 | 날짜 | 메모 |
|---|---|---|---|---|
| V-01 | 일부 — 기본 디스플레이 2560×1440 px · 밀도 1.0(160 dpi) · 앱 영역 ≈ 2560×1268 dp(상태 표시줄 76 px · 하단 바 96 px 제외) → WIDE. **FormFactorConsole 프리셋(12.9″ Portrait 등)과 분할 화면 한 칸의 dp는 아직** | 맥 에뮬레이터(Pleos Connect v2.1.0, API 34) | 2026-11-10 | `docs/screenshots/font-size/pleos-*.png`(2560×1440) 캡처에서 잼: 여백 `Space.s7` 48 dp = 48 px, `Size.dockIcon` 112 dp = 112 px |
| V-02 | 성공 — `includeBuild("backend") { dependencySubstitution { substitute(module("kr.autolog:vehicle-core")).using(project(":vehicle-core")) } }` 그대로 동작. 대안(include) 불필요 | WSL | 2026-11-09 | `:backend:vehicle-core` 태스크로 빌드됨 |
| V-03 | | 맥 에뮬레이터 | | |
| V-04 | | 맥 에뮬레이터 | | |
| V-05 | 지원 — Asta Sans GSUB 기능 목록에 `tnum` 있음(aalt·calt·ccmp·frac·fwid·liga·ordn·sinf·ss01·subs·sups·tnum·vert·vrt2) | WSL | 2026-11-09 | `fontFeatureSettings = "tnum"` 그대로 사용 |
| V-06 | 일부 — 에뮬레이터는 1 sp = 1 px(밀도 1.0). 실제 mm는 차량 패널의 물리 크기에 달려 에뮬레이터로는 정하지 못한다. 대신 사용자가 에뮬레이터에서 PR #4 글자 크기(title 44 · body 34 · caption 28 sp)를 보고 괜찮다고 확인. 글자 하한은 caption 28 sp(옛 fine 22 sp는 504 dp 좁은 창 전용이라 디자인 언어 1.33에서 삭제) | 맥 에뮬레이터(Pleos Connect v2.1.0) | 2026-11-10 | 실차 패널 크기를 알게 되면 mm로 다시 계산 |
| V-07 | | 실차 | | |
| V-08 | | 맥 에뮬레이터 | | |
| V-09 | 정상 — Roborazzi 1.32.2 + Robolectric 4.14.1(SDK 34, NATIVE)에서 Asta Sans 한글·숫자 렌더링 확인(`components_tall_*.png`) | WSL | 2026-11-09 | **무한 애니메이션(CircularProgressIndicator 등)이 화면에 있으면 캡처 1장당 약 3분 대기** → 스크린샷에는 로딩 회전을 넣지 않는다 |
| V-10 | | WSL | | |
| V-11 | | 맥 에뮬레이터 | | |
| V-12 | | 맥 에뮬레이터 | | |
| V-13 | | 맥 에뮬레이터 | | |
| V-14 | | — | | Backend sdk-findings #5 |
| V-15 | | 맥 에뮬레이터 | | |
| V-16 | | 맥 에뮬레이터 | | |
| V-17 | | 맥 에뮬레이터 | | |
| V-18 | | 맥 에뮬레이터 | | 모션 — 애니메이션 배율이 0이면 모든 모션이 꺼진다. WSL에서는 `recordRoborazziDemoDebug`가 남기는 `src/test/screenshots/motion/*.png` 프레임 시트로만 확인 |

## 구현 중 발견한 사실 (2026-11-09, WSL)

- **Robolectric에서 한국어 구문 줄바꿈(`LineBreak.WordBreak.Phrase`)이 적용되지 않는다** — 스크린샷에서 "달 / 렸어요"처럼 어절 중간이 끊긴다. 실기기(Android 13+)에서는 적용될 것으로 보이나 맥 에뮬레이터에서 확인 필요. 그동안은 끊기기 쉬운 문장을 나눠 설계한다.
- **Robolectric 기본 화면(320×470dp)에서는 큰 타일이 화면 밖으로 밀려 클릭 테스트가 실패한다** — 동작 테스트에 `@Config(qualifiers = "w1024dp-h640dp")`를 준다.
- **무한 애니메이션이 화면에 있으면 Roborazzi 캡처 1장당 약 3분** — 스크린샷에 로딩 회전을 넣지 않는다(V-09 메모).
- **모션 테스트에서 시계를 멈추면(`mainClock.autoAdvance = false`) `captureToImage`가 다시 그리기를 기다리다 멈춘다**(2026-11-10) — 프레임은 Roborazzi `captureRoboImage`로 찍고(`screenshot/MotionFrames.kt`), 상태를 바꾼 뒤 `Snapshot.sendApplyNotifications()`로 알려야 다음 프레임이 바뀐 값을 그린다. 0초 스프링(`snap`)으로 끝나는 `MutableTransitionState`는 이 조건에서 끝나지 않아 `ModalHost`는 `Animatable`로 만들었다.
