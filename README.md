# AutoLog — 차가 스스로 쓰는 운행·지출 장부

> 운전만 하세요. 기록은 차가 합니다.

2026 현대자동차그룹 차량용 앱 개발 공모전 (Pleos App Market). 기획·기능·기술 명세는 [`docs/`](docs/)에 있다: [기획서](docs/AutoLog_기획서.md) · [기능명세](docs/AutoLog_기능명세.md) · [기술명세](docs/AutoLog_기술명세.md) · [DB 스키마](docs/AutoLog_DB스키마.md).

> 2026-11-11 [AutoLog-Frontend](https://github.com/hmg-app-challenge-2026/AutoLog-Frontend)(화면)와 [AutoLog-Backend](https://github.com/hmg-app-challenge-2026/AutoLog-Backend)(차량 로직·서버)를 이 리포 하나로 합쳤다. 두 리포의 커밋 기록·브랜치 이름은 그대로 들어 있다(커밋 해시도 같다).

## 먼저 읽을 문서

1. [CONVENTIONS.md](CONVENTIONS.md) — 분업·브랜치·커밋·코드 스타일(1~10장 공통, 11장 화면)
2. 화면을 만든다면: [프론트 설계 스펙](docs/superpowers/specs/2026-11-09-autolog-frontend-design.md) — 무엇을 왜, 어떻게 만드는지(결정·구조·화면·일정) · [디자인 언어](docs/design-language.md) — 철학·원칙·토큰·모션·컴포넌트
3. 화면 ↔ 로직 연결 상태: [백엔드 연결 기록](docs/integration/backend-link.md) · [남은 문제·정책 논의](docs/integration/open-issues.md)

## 같이 개발하기

분업·브랜치·커밋·코드 스타일은 **[CONVENTIONS.md](CONVENTIONS.md)** 를 따른다. PR 전에:

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck :shared:test :vehicle-core:test :relay:test
./gradlew :vehicle-app:testDemoDebugUnitTest :vehicle-app:recordRoborazziDemoDebug :vehicle-app:assembleDemoDebug
```

## 구조

| 경로 | 무엇 | 실행 위치 | 차량 APK에 포함 |
|---|---|---|---|
| `shared/` | inbox payload DTO (Kotlin JVM) | — | ✅ (vehicle-core 가 의존) |
| `vehicle-core/` | 차량 로직 — SDK·운행 기록·Room·inbox·엑셀·메일 (화면 없음) | Pleos 에뮬레이터 · 헤드유닛 | ✅ (vehicle-app 이 의존) |
| `vehicle-app/` | 차량 화면 (Kotlin · Jetpack Compose · Material 3). `vehicle-core` 의 공개 계약(`core/Contracts.kt`)·`AutoLog.create`만 사용 | Pleos 에뮬레이터 · 헤드유닛 | ✅ |
| `relay/` | 중계 서버 (Spring Boot + PostgreSQL + MinIO) | 서버 VM | ❌ |
| `web/admin` | 운영 웹 (React + TS + Vite) | nginx 정적 | ❌ |
| `web/camera` | 폰 영수증 카메라 페이지 (HTML 1장) | nginx 정적 | ❌ |
| `ops/` | docker-compose, nginx, .env.example | 서버 VM | ❌ |
| `scripts/` | Pleos 에뮬레이터 손 시험 도우미(`emu.sh`) | 개발 PC | ❌ |

의존 방향: `vehicle-app` → `vehicle-core` → `shared`. 화면과 로직의 경계는 `vehicle-core/.../core/Contracts.kt`·`AutoLog.kt`.

원칙: **운행 기록의 원본은 차량 Room.** 서버는 영수증 OCR·기준 정보만 차로 넣어주는 우체통이며, 서버를 꺼도 운행·엑셀·메일은 동작한다.

## 빌드

요구: JDK 17+, Android SDK 35, Node 20+, Docker. 실행 검증은 Apple Silicon 맥의 Pleos Connect Emulator(API 34)에서 한다(프론트 스펙 5.5).

```bash
./gradlew :shared:test               # 공용 DTO
./gradlew :vehicle-core:test         # 차량 로직 단위 테스트 (화면·에뮬레이터 없이)
./gradlew :vehicle-app:installDemoDebug     # 차량 앱 — 전부 Fake. 왼쪽 위 구석을 길게 누르면 데모 패널
./gradlew :vehicle-app:installVehicleDebug  # 차량 앱 — Pleos 에뮬레이터용(vehicle-core 실구현 AutoLog.create)
./gradlew :relay:bootJar             # 서버 jar → relay/build/libs/relay.jar
```

```bash
cd web/admin && npm install && npm run dev     # 운영 웹 개발 서버
```

```bash
cd ops && cp .env.example .env && docker compose up -d   # 서버 일체 (relay jar·web 빌드 후)
```

APK에 무엇이 들어갔는지 확인: Android Studio **Build → Analyze APK** 또는
`./gradlew :vehicle-app:dependencies --configuration vehicleReleaseRuntimeClasspath`.

개발 환경은 [docs/dev-setup.md](docs/dev-setup.md), 디자인 게이트는 [docs/design-gate-2026-11-11.md](docs/design-gate-2026-11-11.md).

## Pleos SDK

Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`. 검증 결과는 [`vehicle-core/docs/sdk-findings.md`](vehicle-core/docs/sdk-findings.md). 에뮬레이터 손 시험: [`scripts/emu.sh`](scripts/emu.sh).
