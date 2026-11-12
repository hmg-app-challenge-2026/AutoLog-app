# AutoLog 개발 컨벤션

> 로직 2명(앱 A·B)과 프론트 1명이 한 저장소에서 같이 개발하기 위한 규칙이다.
> 근거 문서는 기술명세 8장(분업)과 기능명세 3장(담당 열)이다. 이 문서와 명세가 다르면 **명세를 따르고 이 문서를 고친다.**
> 화면(`vehicle-app/`·`web/`)의 설계 기준은 [프론트 설계 스펙](docs/superpowers/specs/2026-11-09-autolog-frontend-design.md)과 [디자인 언어](docs/design-language.md)이고, 화면에만 해당하는 규칙은 [11장](#11-화면vehicle-app--web-규칙)에 있다.
>
> **2026-11-11 AutoLog-Frontend와 AutoLog-Backend를 이 리포(AutoLog-app) 하나로 합쳤다.** 두 리포의 커밋 기록과 브랜치는 그대로 들어 있다. 예전 프론트 리포의 CONVENTIONS는 11장으로 옮겼다(내용은 그대로, submodule 관련 문장만 고침).
>
> 체크리스트만 보려면 [10장](#10-pr-올리기-전-체크리스트)으로 가면 된다.

---

## 1. 누가 어디를 만드나

### 1.1 담당

| | 앱 A — 차량 데이터·기록·출력 코어 | 앱 B — 맥락·판단·중계 | 프론트 |
|---|---|---|---|
| 한 줄 | 계기판을 읽어 운행을 만들고, 잃지 않고, 엑셀로 내보낸다 | 운행에 "어디로·왜·누가"를 붙이고, 폰·관리자 입력을 차로 잇고, 메일로 보낸다 | 모든 화면 |
| SDK | Vehicle SDK (Odometer, Gear, CarInfo, Fuel) | NaviHelper, Fused Location, Gleo AI (STT·TTS) | — |
| 기능 ID | F-01–05, 10–14, 16, 21, 23 | F-06–09, 15, 17–20, 22, 24, 30, 40–43, 50–54(API) | 화면 전부 |

### 1.2 폴더 소유

**소유자가 아닌 사람은 그 폴더를 고치지 않는다.** 꼭 고쳐야 하면 PR을 올리고 소유자에게 리뷰를 받는다.

| 경로 | 소유 | 내용 |
|---|---|---|
| `vehicle-core/.../core/` | **A·B 공동** | `Contracts.kt`(경계), `SdkCall.kt`(콜백→코루틴 어댑터) 같은 공용 코드 |
| `vehicle-core/.../fake/` | **A·B 공동** | `Contracts.kt`의 Fake 구현 (각자 자기 인터페이스 것을 만든다) |
| `vehicle-core/.../vehicle/` | A | VehicleGateway |
| `vehicle-core/.../session/` | A | TripSessionManager, TripRecordingService·알림, Recovery, DrivingJudge |
| `vehicle-core/.../data/` | A | Room 엔티티·DAO·AutoLogDb, InboxSyncWorker, RelayApi |
| `vehicle-core/.../report/` | A | ReportBuilder(XLSX), ReviewCalculator |
| `vehicle-core/.../navi/` · `location/` · `speech/` | B | NaviGateway, LocationGateway, STT·TTS, 음성 파서 |
| `vehicle-core/.../domain/` | B | PurposeRules, DriverResolver, UserOrdering, PlaceSuggester, ReceiptMatcher |
| `vehicle-core/.../mail/` | B | Mailer(SMTP), SmtpCredentialStore |
| `shared/` | B (A 리뷰 필수) | inbox payload DTO. 서버·앱이 함께 쓰므로 바꿀 때 A가 꼭 리뷰한다 |
| `relay/` | B | 중계 서버 전체 |
| `vehicle-app/` | 프론트 | Compose 화면 + ViewModel + Repository(차량 앱). 화면 규칙은 11장 |
| `web/` | 프론트 (API 계약은 B) | 운영 웹, 카메라 페이지 |
| `docs/design-language.md` · `docs/superpowers/` · `docs/integration/` | 프론트 | 화면 설계 스펙·디자인 언어·백엔드 연결 기록 |
| `ops/` · 루트 Gradle 설정 · 이 문서 | 공동 | 바꿀 때 상대방 리뷰 |

> 명세 7장 트리에는 로직이 `app/` 아래로 그려져 있지만, 실제 저장소는 로직을 `vehicle-core/`로 분리했다. 패키지 이름(`kr.autolog.vehicle.*`)은 명세와 같다.
> 화면 앱은 `vehicle-app/`(Gradle `:vehicle-app`)이다. 예전 백엔드 리포의 `app/`(화면 뼈대)은 리포를 합치면서 지웠다(프론트 스펙 R-6).

### 1.3 의존 방향

```
vehicle-app (화면) ──► vehicle-core ──► shared ◄── relay
                 │
                 └─ 화면은 core/Contracts.kt 의 인터페이스만 쓴다
```

- `vehicle-app`은 `vehicle-core`의 `core`·`fake` 패키지만 import한다. `session`, `data` 같은 내부 패키지를 직접 쓰지 않는다.
- A·B도 상대방 패키지를 직접 import하지 않고 `Contracts.kt`를 거친다. 예를 들어 A의 `session`이 B의 `domain.PurposeRules`를 직접 부르지 않고 `PurposeEngine` 인터페이스로 받는다.
- `shared`에는 `kotlinx-serialization` 외의 의존성을 추가하지 않는다 (APK에 들어간다).
- `vehicle-app`과 `vehicle-core`는 `relay`에 의존하지 않는다.

---

## 2. 브랜치

| 브랜치 | 용도 |
|---|---|
| `main` | 항상 빌드되는 상태. **직접 push 금지**, PR로만 합친다 |
| `a/<기능ID>-<요약>` | 앱 A 작업. 예: `a/F-05-odometer-distance` |
| `b/<기능ID>-<요약>` | 앱 B 작업. 예: `b/F-41-gemini-ocr` |
| `feat/<요약>` · `fix/<요약>` · `docs/<요약>` | 프론트 작업(기능 · 버그만 · 화면 설계 문서만). 예: `feat/main-screen`, `fix/emulator-qa`, `docs/front-back-issues`. 2026-11-09 프론트 결정으로 `fe/` 대신 이 이름을 쓴다(11.2) |
| `contract/<요약>` | `Contracts.kt`·`shared` 변경 전용. 예: `contract/receipt-api-edits` |

- 요약은 영어 소문자와 `-`로 쓴다.
- 브랜치는 짧게 유지한다. **2일 안에 PR**을 올리는 게 목표다. 큰 기능은 나눠서 올린다.
- 작업 전에 `git pull --rebase origin main`으로 최신 main을 받는다.

---

## 3. 커밋 메시지

```
<type>(<scope>): <한국어 요약> (<기능ID>)

<필요하면 본문: 왜 바꿨는지>
```

예시:
```
feat(vehicle): Odometer 리스너를 Flow로 노출 (F-05)
fix(session): P 전환 시 체크포인트 저장 누락 수정 (F-10)
feat(relay): 영수증 Gemini OCR 파이프라인 (F-41)
test(domain): 출근 추천 규칙 단위 테스트 (F-08)
docs(sdk): Odometer 소수점·갱신 주기 측정 결과
```

| type | 언제 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `test` | 테스트만 추가·수정 |
| `refactor` | 동작 변화 없는 구조 변경 |
| `docs` | 문서 (`sdk-findings.md`, README, 이 문서) |
| `build` | Gradle, 의존성, Docker |
| `chore` | 그 외 (설정 파일 등) |

**scope**는 패키지나 모듈 이름을 쓴다: `core` `fake` `vehicle` `session` `data` `report` `navi` `location` `speech` `domain` `mail` `shared` `relay` `app` `web` `ops` `sdk` `build`
(화면 작업의 scope는 11.2: `app` `design` `nav` `repo` `home` … — `app`은 차량 앱 공통이다)

- 요약은 50자 안팎, 마침표 없이 쓴다.
- 기능 ID가 있으면 끝에 붙인다. 명세와 추적이 된다.
- 한 커밋에는 한 가지 일만 담는다.

---

## 4. PR

- PR 설명은 아래 형식으로 쓴다. 저장소에 PR 템플릿 파일은 두지 않는다 (GitHub이 빈 템플릿을 커밋 본문 뒤에 덧붙이기 때문).
- **팁:** 브랜치의 커밋이 하나면 GitHub이 커밋 본문을 PR 설명에 그대로 넣는다. 그 커밋 본문을 이 형식으로 쓰면 PR 설명을 따로 쓰지 않아도 된다.

```markdown
## 무엇을
한두 줄 요약 + 주요 변경 bullet

## 기능 ID
F-05, F-10 … (명세 3장)

## 확인 방법
단위 테스트 이름 / 에뮬레이터 logcat 캡처 / curl 예시. 검증 못 한 것도 적는다.

## 체크리스트 (10장)
- [x] 내 소유 폴더만 고쳤다 (아니면 소유자를 리뷰어로 넣었다)
- [x] `Contracts.kt`·`shared` 변경이면 `contract/` 브랜치이고 Fake도 같이 고쳤다 — 해당 없음
- [x] 새 규칙·계산에 단위 테스트가 있다
- [x] 거리를 추정하지 않는다. 자동값에 `source`·`status`가 있다
- [x] SDK import는 Gateway 패키지 안에만 있다
- [x] 로그·코드에 비밀값·카드번호·정밀 좌표가 없다
- [ ] 검증한 SDK 동작은 `sdk-findings.md`에 적었다 — 못 지킨 항목은 비워 두고 이유를 적는다
```

- **머지 조건:** 리뷰어 1명 승인 + CI 통과(`.github/workflows/ci.yml` — ktlint · 모듈별 테스트 · 화면 스크린샷 기록 · 빌드) + 충돌 없음.
- 머지 방식은 **Squash merge**다. PR 제목이 커밋 메시지가 되므로 3장 형식으로 쓴다.
- 리뷰어는 기본적으로 상대방(A↔B)이다. 프론트가 쓰는 인터페이스가 바뀌면 프론트도 리뷰어로 넣는다.
- 리뷰는 **24시간 안에** 한다. 급하면 메신저로 알린다.
- PR 하나는 리뷰하기 쉽게 **400줄 이하**를 목표로 한다. 테스트·생성 파일은 빼고 센다.

---

## 5. `Contracts.kt`와 `shared` 바꾸기

이 두 곳은 세 사람(또는 앱과 서버)이 동시에 쓰는 약속이라, 다른 코드보다 엄격하게 다룬다.

1. 다른 작업과 섞지 말고 **`contract/` 브랜치로 따로** PR을 올린다.
2. **A·B 둘 다 승인**해야 머지한다. 화면에 영향이 있으면 프론트도 승인한다.
3. 시그니처를 바꾸면 **같은 PR에서 Fake 구현도 같이 고친다.** 그래야 프론트 화면이 깨지지 않는다.
4. `shared` DTO는 **필드 추가는 기본값과 함께**(`val x: String? = null`) 한다. 이미 배포된 앱이 새 필드 때문에 깨지지 않게 하기 위해서다. 필드 삭제나 이름 변경은 앱과 서버를 같은 PR에서 함께 고친다.
5. 인터페이스 함수마다 KDoc 한 줄로 **누가 부르는지**(프론트 / 앱 A / 앱 B)를 적는다.

---

## 6. Kotlin 코드 스타일

### 6.1 포맷 — 도구가 맞춘다

- 스타일은 [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)을 따르고, 세부 설정은 `.editorconfig`에 있다.
- **커밋 전에 `./gradlew ktlintFormat`** 을 실행한다. CI는 `./gradlew ktlintCheck`로 검사한다.
- 들여쓰기 4칸, 한 줄 최대 120자, 여러 줄 인자 목록에는 trailing comma를 붙인다.
- Android Studio 설정: Settings → Editor → Code Style → Kotlin → "Set from… → Kotlin style guide". `.editorconfig`는 자동으로 적용된다.

### 6.2 이름

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스·인터페이스 | PascalCase, 역할이 드러나게 | `TripSessionManager`, `VehicleGateway`, `PurposeEngine` |
| SDK 감싸는 클래스 | `<SDK>Gateway` | `VehicleGateway`, `NaviGateway`, `LocationGateway` |
| Contracts 인터페이스 | `<도메인>Api` / `<도메인>Engine` / `<도메인>Store` | `TripSessionApi`, `ReceiptApi`, `LocalStore` |
| Fake 구현 | `Fake<인터페이스 이름>` | `FakeTripSession`, `FakeReceiptApi` |
| Room 엔티티 | `<이름>Entity`, 테이블은 복수 snake_case | `TripEntity` → `trips` |
| DAO | `<이름>Dao` | `TripDao` |
| 함수·변수 | camelCase, 함수는 동사로 시작 | `readTotalDistance()`, `liveKm` |
| 상수 | UPPER_SNAKE_CASE, `companion object`나 top-level `const val` | `T_STOP_SEC = 180` |
| Boolean | `is` / `has` / `should` 접두어 | `isDriving`, `shouldProposePlace()` |
| Flow·StateFlow | 명사로, `Flow` 접미어 붙이지 않음 | `val state: StateFlow<State>` |

### 6.3 단위가 있는 값

헷갈리기 쉬운 값은 **이름에 단위를 붙인다.**

| 값 | 타입 | 이름 예 | 규칙 |
|---|---|---|---|
| 거리 | `Double` (km) | `distanceKm`, `startOdometerKm` | 저장은 원본 그대로, 표시할 때 소수 1자리 |
| 시각 | `Long` (epoch millis, UTC) | `startTime`, `lastSeenAt` | 저장·계산은 UTC millis, 화면·엑셀에서만 `Asia/Seoul`로 변환 |
| 기간 | `Long` 또는 `Duration` | `stopDurationSec`, `T_STOP_SEC` | 초는 `Sec`, 밀리초는 `Ms` |
| 금액 | `Int` (원) | `amountWon`, `discountWon` | 부동소수점 금지 |
| 연료량 | `Double` (L) | `liters` | |
| 좌표 | `Double` | `lat`, `lng` | |
| 반경·정확도 | `Int` / `Double` (m) | `radiusM`, `locationAccuracyM` | |

서버 API와 DB의 시각은 ISO 8601 `+09:00` 문자열로 주고받는다(명세 4.3). 앱 안에서는 millis로 바꿔서 쓴다.

---

## 7. 설계 규칙 — AutoLog에서 꼭 지킬 것

명세의 원칙을 코드 규칙으로 옮긴 것이다. 리뷰할 때 이 목록을 기준으로 본다.

### 7.1 데이터

1. **거리는 추정하지 않는다.** Odometer 조회에 실패하면 `distanceKm = null`, `distanceSource = UNAVAILABLE`로 둔다. GPS나 이전 값으로 채우지 않는다. (F-05)
2. **모든 자동값에 출처와 상태를 붙인다.** 거리·목적·운전자·장소·금액에는 `*Source`와 `status` 필드가 있어야 한다. 사용자가 고친 값은 원본을 덮지 말고 별도 필드에 기록한다. (`rawSnapshotJson` 보존)
3. **운행·비용의 원본은 Room이다.** 차에서 서버로 운행 데이터를 올리는 코드를 만들지 않는다. 서버에서 차로 오는 방향은 `inbox` 하나뿐이다.
4. **Room에 바로 쓴다.** 운행 시작, 10초 체크포인트, P 전환은 메모리에만 두지 말고 즉시 커밋한다.
5. **미확정은 미확정으로 둔다.** 근거가 부족하면 `UNDETERMINED`이나 `null`로 두고, 업무용으로 임의 분류하지 않는다.

### 7.2 SDK 호출

1. **SDK는 Gateway 안에서만 부른다.** `ai.pleos.*` import는 `vehicle/`, `navi/`, `location/`, `speech/` 패키지 밖에 나오면 안 된다.
2. **콜백은 `core/SdkCall.kt`의 `sdkCall`·`sdkListenerFlow`로 감싼다.** Gateway는 콜백 대신 `suspend fun`이나 `Flow`를 밖으로 내보낸다.
3. **SDK 호출 순서:** initialize → capability 확인 → get → listener 등록 → (종료 시) unregister → release. (명세 2.4)
4. **확인되지 않은 SDK 동작에는 `// [검증 필요]`를 단다.** 검증하면 주석을 지우고 결과를 `vehicle-core/docs/sdk-findings.md`에 적는다.

### 7.3 코루틴

- `GlobalScope`는 쓰지 않는다. 서비스는 `lifecycleScope`, 그 외는 주입받은 `CoroutineScope`를 쓴다.
- `Dispatchers.IO` 같은 디스패처는 생성자로 주입받는다. 테스트에서 바꿔 끼우기 위해서다.
- 화면에 노출하는 상태는 `StateFlow`로, 한 번만 일어나는 이벤트는 `SharedFlow`로 내보낸다. 내부에서는 `MutableStateFlow`를 쓰되 `private`으로 감춘다.

### 7.4 테스트하기 쉽게

- **`domain/`과 `report/`의 계산 로직은 Android 의존 없이 순수 함수로 짠다.** `Context`, `Log` 등을 쓰지 않는다. 그래야 에뮬레이터 없이 JVM 테스트로 돈다.
- 현재 시각은 `System.currentTimeMillis()`를 직접 부르지 말고 `Clock`을 주입받는다. 시간대별 규칙(출근 05–11시 등)을 테스트하려면 필요하다.

### 7.5 에러 처리

- 실패할 수 있는 SDK·네트워크 작업은 예외를 Gateway나 Repository 안에서 잡아서 **값으로** 돌려준다(`null`, `Result`, sealed class).
- `Contracts.kt` 바깥으로 예외를 던지지 않는다. 화면이 try-catch를 하지 않아도 되게 하기 위해서다.
- 빈 `catch {}`는 금지한다. 최소한 로그를 남긴다.

### 7.6 로그

- 태그는 `AutoLog/<패키지>` 형식이다. 예: `AutoLog/vehicle`, `AutoLog/session`.
- SDK 검증 단계에서는 원본 값과 시각을 그대로 찍는다. logcat이 A·B의 완료 기준이다(명세 8.2).
- **로그에 남기면 안 되는 것:** 카드번호(뒷 4자리 외), SMTP 비밀번호, 기기 토큰, Gemini 키, 정밀 좌표(소수 3자리까지만).

---

## 8. 서버(relay) 규칙

- **패키지는 기능별로 나눈다:** `device/`, `inbox/`, `receipt/`, `admin/`, `common/`. 각 패키지 안에 `*Controller`, `*Service`, `*Repository`를 둔다.
- 컨트롤러는 얇게 유지한다. 요청을 검증하고 서비스를 부르는 것까지만 한다.
- 경로는 차량 API `/v1/...`, 관리자 API `/admin/...`, 카메라 `/r/...`를 따른다(명세 4.3).
- inbox payload는 반드시 `shared`의 DTO를 쓴다. 서버 안에서 같은 모양의 클래스를 따로 만들지 않는다.
- 에러 응답 형식은 `{ "code": "RECEIPT_DUPLICATE", "message": "..." }`이다. HTTP 상태 코드는 명세를 따른다(중복 409 등).
- **Flyway 마이그레이션은 한 번 main에 들어가면 고치지 않는다.** 바꿀 게 있으면 `V2__<설명>.sql`처럼 새 파일을 만든다.
- 비밀값(Gemini 키, DB 비밀번호)은 환경변수로만 받는다. `application.yml`에는 `${ENV:기본값}` 형태로 개발용 기본값만 둔다.

---

## 9. 테스트

| 대상 | 무엇을 | 어디에 |
|---|---|---|
| `domain/` 규칙 | **규칙 하나당 테스트 1개 이상** (F-08 수용 기준) | `vehicle-core/src/test` |
| `report/` ReviewCalculator | 검토 7종 각각 1개 이상, 오탐 0 (F-23) | `vehicle-core/src/test` |
| 거리 계산·세션 상태 전이 | 정상, 조회 실패, 경계값 (60초, 0.3 km, 1,500 km) | `vehicle-core/src/test` |
| `shared` DTO | 직렬화 왕복 | `shared/src/test` |
| `relay` | 서비스 단위 테스트, 검산·카드번호 마스킹 | `relay/src/test` |
| SDK 연동 | 에뮬레이터 logcat 확인 → `sdk-findings.md`에 기록 | — |

- 테스트 이름은 백틱으로 **동작을 문장으로** 쓴다: ``fun `평일 아침 자택에서 회사로 가면 출근을 추천한다`()``
- Given / When / Then 순서로 빈 줄을 넣어 구분한다.
- 테스트에서는 실제 SDK 대신 `fake/`의 구현을 쓴다.

---

## 10. PR 올리기 전 체크리스트

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck :shared:test :vehicle-core:test :relay:test
./gradlew :vehicle-app:testDemoDebugUnitTest :vehicle-app:assembleDemoDebug :vehicle-app:assembleVehicleDebug
```
화면을 바꿨으면 11.5의 스크린샷·웹 명령도 돌린다.

- [ ] 내 소유 폴더만 고쳤다 (아니면 소유자를 리뷰어로 넣었다)
- [ ] `Contracts.kt`·`shared`를 바꿨다면 `contract/` 브랜치이고, Fake도 같이 고쳤다
- [ ] 새 규칙이나 계산에는 단위 테스트가 있다
- [ ] 거리를 추정하는 코드가 없다. 자동값에는 `source`와 `status`가 있다
- [ ] SDK import가 Gateway 패키지 밖에 없다
- [ ] 로그와 코드에 비밀값·카드번호·정밀 좌표가 없다
- [ ] 검증한 SDK 동작은 `sdk-findings.md`에 적었다
- [ ] 커밋과 PR 제목이 `type(scope): 요약 (기능ID)` 형식이다

---

## 부록: 명세 문서 고치기

`docs/`(기획서·기능명세·기술명세)도 코드처럼 PR로 고친다. 커밋은 `docs(spec): <바뀐 장> 요약` 형식이고, 담당·인터페이스·일정이 바뀌면 상대방 리뷰를 받는다. 공개 저장소이므로 실명·연락처·계정·키는 적지 않는다.

---

## 11. 화면(`vehicle-app` · `web`) 규칙

> 예전 AutoLog-Frontend 리포의 CONVENTIONS다(2026-11-11 리포를 합치며 이 장으로 옮김). 화면 코드를 쓰거나 고치는 모든 사람(과 AI 도구)이 따른다.
> 설계는 [설계 스펙](docs/superpowers/specs/2026-11-09-autolog-frontend-design.md), 디자인은 [디자인 언어](docs/design-language.md)가 기준이다. 이 장과 스펙이 다르면 **스펙을 따르고 이 장을 고친다.**
> 1~10장(공통 규칙)은 그대로 따르고, 화면에서 다른 점만 여기 적는다.

### 11.1 소유와 경계

| 경로 | 소유 | 규칙 |
|---|---|---|
| `vehicle-app/` · `web/` · 화면 문서(`docs/design-language.md`·`docs/superpowers/`·`docs/integration/`) | 프론트 | 자유롭게 고친다 |
| `shared/` · `vehicle-core/` · `relay/` | 앱 A·B | **1.2 소유 규칙을 따른다** — 프론트가 고쳐야 하면 소유자를 리뷰어로 넣는다(예전엔 submodule이라 이 리포에서 아예 고치지 않았다) |
| `vehicle-app/.../data/fake/` | 프론트 | 데모·테스트용 앱 A 가짜(데모 조작 — 거리 늘리기·놓친 운행·복구 — 포함). 실제 앱(vehicle flavor)은 쓰지 않는다 |
| `vehicle-app/.../data/upload/` | 프론트(임시) | 사내 서버 업로드 자리표시 계약. 백엔드 계약(R-4)이 오면 삭제한다 |
| `vehicle-app/.../overlay/` · `src/vehicle/.../overlay/` | 프론트 | 운행 기록 알약(스펙 6.12). 경계·표시 규칙은 main, Android 창 구현(`RecordingPill`)은 vehicle flavor에만. 설정 화면은 `data/repository/OverlayPermissionRepository`로만 권한을 본다 |

- `ai.pleos.*` import 금지(스펙 6.1).
- `ui/` 패키지는 `data/repository`의 인터페이스만 쓴다. `kr.autolog.vehicle.core`의 서비스 인터페이스를 import하지 않는다(값 enum은 가능).
- Backend 내부 패키지(`session`, `data`, `domain`, `navi` …) import 금지. `core`·`fake`·`shared`만.

### 11.2 브랜치·커밋·PR

- 브랜치: **작업(계획의 Task) 하나 = 브랜치 하나**, `feat/<요약>`(영어 소문자 + `-`). 예: `feat/foundation`, `feat/main-screen`, `feat/review-trip`. 앞 작업 브랜치에서 갈라져 나와 이어 쌓는다. 버그만 고치면 `fix/<요약>`, 설계 문서만 바꾸면 `docs/<요약>`(2026-11-09 사용자 결정 — 예전 `fe/` 접두어는 쓰지 않는다).
- `main`에 직접 push하지 않는다. PR + 리뷰 1명 + CI 통과 → **Squash merge**.
- 커밋: `<type>(<scope>): <한국어 요약> (<기능ID>)`. type은 Backend와 같다(feat·fix·test·refactor·docs·build·chore).
- scope: `app`(차량 앱 공통) `design` `nav` `repo` `profile` `home` `review` `receipt` `report` `history` `settings` `admin` `camera` `build` `spec`
- 예: `feat(profile): 넷플릭스식 운전자 선택 화면 (F-15)` · `fix(home): 기록 종료도 자동 종료와 같은 5초 종료 대기로 (F-04)`. 예전 `build(backend): submodule을 …로 갱신`은 리포를 합친 뒤로 없다
- PR 설명 형식과 400줄 목표는 4장과 같다. 화면 PR에는 **스크린샷(WIDE·NARROW·TALL × 다크·라이트)**을 첨부한다(Roborazzi 출력).

### 11.3 코드 스타일

- Kotlin: 6장(ktlint, 이름, 단위 접미어)을 그대로 따른다. 커밋 전 `./gradlew ktlintFormat`.
- Compose 파일 구성: 화면 하나 = `XxxRoute.kt`(ViewModel 연결) · `XxxScreen.kt`(순수 UI + Preview) · `XxxViewModel.kt` · `XxxUiState.kt`(UiState·Action·Effect).
- Composable 이름은 PascalCase 명사, 파라미터 순서: 필수 데이터 → `onXxx` 콜백 → `modifier: Modifier = Modifier` (마지막).
- **누를 수 있는 요소는 모두 `Modifier.tappable`**(`ui/design/components/Press.kt`)로 만든다. 입력 잠금(스펙 6.9)·누름 피드백·햅틱이 한곳에 있다 — `clickable`을 직접 쓰지 않는다. 정차·주행 중엔 화면이 그대로 남고 잠금만 걸린다(D-31) — 그때도 눌려야 하는 동작(지금은 [기록 종료] 하나)만 `whileDriving = true`.
- 화면 바깥 여백은 `screenMargin()`(창 크기별 하나). 화면마다 `Space.s7`/`tallMargin`을 따로 고르지 않는다.
- 창 크기에 따라 달라지는 글자는 `AutoLogTheme.windowType.<역할>`(`ui/design/WindowType.kt`). 화면·컴포넌트에서 `if (LocalWindowLayout.current == NARROW) type.a else type.b`로 글자를 고르지 않는다 — 새 역할이 필요하면 그 표에 한 줄 더한다.
- 입력 칸이 여러 개인 화면은 칸 정의(이름·단위·입력 방법·계약 키)를 enum 하나에 모은다(예: `ui/receipt/FuelField.kt`). 칸마다 `when`을 여러 곳에 두지 않는다.
- ViewModel·Reducer는 화면 문구를 모른다. 사람에게 보일 이름·오류는 sealed 타입(예: `FuelTripLabel`, `FuelEntryError`)으로 내보내고 화면이 `strings.xml`로 바꾼다.
- 화면 하나의 상태 계산은 `XxxReducer.kt`의 순수 함수(`reduceXxx`)로 두고 Repository 값 타입만 입력으로 받는다. `data/fake`·`data/upload` 타입은 `ui/`에 들어오지 않는다(스펙 6.6).
- 색·글자·간격·모양·모션은 **반드시 `AutoLogTheme` 토큰**. 리터럴 `Color(0xFF…)`, `16.dp`, `24.sp`를 화면 코드에 쓰지 않는다(토큰 정의 파일 제외).
- 모션은 `ui/design/motion/`의 도우미로만(디자인 언어 8.2): 팝업은 `ModalHost`로 감싸고(닫힘 모션), 같은 자리 교체 `FadeThrough`, 달 이동 `DirectionalContent`, 생겼다 사라지는 것 `ExpandVisibility`·`ExpandSwap`, 선택 `animateSelectColor`. 화면 코드에 `AnimatedContent`·`animate*AsState`·`spring(...)`·`tween(...)`을 직접 쓰지 않는다. 화면 전환 모양은 `FullApp`이 스택 변화로 정한다 — 화면은 Navigator만 부른다.
- **버튼 바로 위 설명글은 가운데** — 화면은 `ButtonNote`, 팝업 안은 `ModalText`(2026-11-10 사용자 지시). `Text`를 버튼 위에 왼쪽 정렬로 두지 않는다.
- 문자열은 `strings.xml`. 화면 코드에 한국어 리터럴을 쓰지 않는다(Preview·테스트 제외).
- TypeScript(web/admin): ESLint 기본 + `strict: true`. 컴포넌트 PascalCase, 훅 `useXxx`, API 함수 동사형.

### 11.4 테스트

| 대상 | 규칙 |
|---|---|
| ViewModel | 화면마다 1개 이상. 정상·오류·잠금 상태 각 1개 이상 |
| 디자인 토큰 | 토큰을 바꾸면 `ContrastTest` 통과 필수 |
| 스크린샷 | 화면을 추가·변경하면 `./gradlew :vehicle-app:recordRoborazziDemoDebug`로 로컬에 기록해 검토한다. 파일은 창 크기별 폴더 `src/test/screenshots/{wide,narrow}/<이름>_<dark|light>.png`(`captureAll`이 정한다). **실제 Pleos 화면 두 크기만** 찍는다(2026-11-10 사용자 지시 — 근거 없는 비율은 지움): `wide/` = 앱 하나가 앱 영역 전체(Pleos Connect v2.1.0 에뮬레이터 2560×1268 dp), `narrow/` = 1:1 분할 한 칸(1280×1268 dp, Pleos Window Policy 기본 1:1). 둘 다 밀도 1.0이라 에뮬레이터와 같은 픽셀이다. **현재는 리포에 커밋하지 않는다**(`.gitignore`, 2026-11-09 사용자 결정 — 로컬 검토용). 그래서 CI는 `verifyRoborazzi`(기준 이미지와 비교 — 기준이 없으면 모든 스크린샷 테스트가 "original file was not found"로 실패)가 아니라 `recordRoborazzi`로 찍기만 하고, 캡처는 Actions 결과물 `screenshots`로 올린다(리뷰어가 내려받아 본다). 화면이 그려지다 죽는 문제는 잡지만 픽셀 회귀는 잡지 못한다. 기준 이미지를 커밋하기로 바꾸면 `.gitignore`에서 빼고 CI를 `verifyRoborazzi`로 되돌린 뒤 이 행을 고친다 |
| 컴포넌트 갤러리 | `ui/design/components/` 컴포넌트를 추가·변경하면 `GalleryScreenshotTest`(components·components_tiles·components_forms·components_keypads·components_modal)도 같이 고친다. 갤러리는 지금 컴포넌트 목록과 같아야 한다. UI를 바꾸면 `src/test/screenshots/`를 지우고 전부 다시 기록한다(옛 캡처를 남기지 않는다) |
| 웹 | 폼·API 오류 문구 테스트 |

테스트 이름은 백틱 문장: ``fun `주행이 시작되면 보조 화면을 모두 닫는다`()``.

### 11.5 PR 전 체크리스트(화면)

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck :vehicle-app:testDemoDebugUnitTest :vehicle-app:recordRoborazziDemoDebug :vehicle-app:assembleDemoDebug
(cd web/admin && npm ci && npm run build)   # web을 바꿨을 때 — lint·test 스크립트는 아직 없다(스펙 9.1 목표: ESLint·Vitest)
```

- [ ] 소유 경계를 지켰다(1.2 — 앱 A·B 폴더를 고쳤으면 소유자 리뷰, 금지 import 없음)
- [ ] 리터럴 색·크기 없이 토큰만 썼다
- [ ] 스크린샷 4장(넓은 화면·분할 화면 × 2테마)을 로컬에 기록해 확인했다(커밋하지 않음)
- [ ] 사용 3단계를 지켰다: 주차(FULL) 전용 화면이고, 정차·주행이면 닫히고 Home만 읽기 전용으로 남는다 — 정차·주행 중에도 써야 하는 동작만 `tappable(whileDriving = true)` (스펙 1.1·D-31)
- [ ] 조작 예산 안이다. 자동 진행에는 남은 시간과 1탭 취소가 있다 (스펙 1.2)
- [ ] [디자인 언어 13장 체크리스트](docs/design-language.md#13-리뷰-체크리스트-화면-pr마다)를 통과했다
- [ ] 맥 에뮬레이터 확인 여부를 PR에 적었다
- [ ] 비밀값·카드번호·정밀 좌표가 코드·로그에 없다
