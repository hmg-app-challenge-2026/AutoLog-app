# 차량 앱 나머지 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 이 계획은 작성자가 직접 실행하므로 코드 전문 대신 **파일·인터페이스·테스트·수용 기준**을 적는다. 화면 동작의 상세는 스펙 7장이 기준이고, 구현 패턴은 첫 계획(`2026-11-09-vehicle-app-foundation.md`)의 코드를 그대로 따른다.

**Goal:** Pleos 차량 앱의 나머지 주차(FULL) 전용 화면 — ReviewTrip, 영수증(QR·OCR 확인·주유), 운행기록부·보내기, 이력, 설정 — 을 만들고 Home의 보조 동작을 연결한다.

**Architecture:** 첫 계획과 같다. Compose → ViewModel(`StateFlow<UiState>` + `onAction`) → Repository → `vehicle-core` Contracts(앱 B: 실제 Fake) / `data/interim`(앱 A: 이 리포 Fake). 모든 화면은 `Route`로 열리고 단계가 FULL에서 내려가면 Navigator가 닫는다.

**Spec:** `docs/superpowers/specs/2026-11-09-autolog-frontend-design.md` 7.3~7.9, 8장 · `docs/design-language.md`

**선행 상태:** 브랜치 `feat/foundation`(옛 이름 `fe/vehicle-app-foundation`)(디자인 게이트 리뷰는 사용자가 나중에). 이 계획은 그 위에 `fe/vehicle-app-screens`로 쌓는다.

## Global Constraints

첫 계획의 Global Constraints 전부 + 아래.

- 화면 문구는 `strings.xml`, 색·크기·모션은 토큰만. 조작 예산(스펙 1.2): 목적 확정 1탭(확신 낮으면 2탭) · 영수증 확정 1탭 · 운행기록부 발송 2탭
- 텍스트 입력은 최후의 수단(프리셋 → 최근값 → 음성 → 키보드). 플랫폼 키보드 규제(`VehicleUsageRepository.noKeyboard`)가 켜지면 입력 필드 비활성 + "지금은 입력할 수 없어요"
- 되돌리기 어려운 동작(삭제)만 확인을 묻는다
- 스크린샷에 무한 애니메이션(로딩 회전·음성 듣는 중 애니메이션)을 넣지 않는다. 스크린샷은 로컬 검토용(커밋 안 함)
- 동작 테스트는 `@Config(sdk = [34], qualifiers = "w1024dp-h640dp")`
- `backend/`는 고치지 않는다. 앱 A 계약은 `data/interim`에만 추가한다

---

## Task 1: interim 계약 2 — LocalStore·ReportApi·DeviceApi·UploadApi

**Files:** Modify `data/interim/InterimContracts.kt`, `data/interim/FakeTripSession.kt` · Create `data/interim/FakeLocalStore.kt`, `FakeReportApi.kt`, `FakeDeviceApi.kt`, `FakeUploadApi.kt` · Test `test/.../data/interim/FakeLocalStoreTest.kt`, `FakeTripSessionTest.kt`(추가)

**Interfaces (Produces):**
```kotlin
enum class TripStatus { CONFIRMED, PENDING_REVIEW, DRIVER_UNSET, DISTANCE_UNSET }
enum class UploadState { PENDING, DONE, FAILED }
data class TripRow(tripId, startTime: Long, endTime: Long?, driverUserId: String?, driverName: String?,
    originLabel: String?, destinationLabel: String?, distanceKm: Double?, purpose: PurposeDetail?,
    status: TripStatus, upload: UploadState?)
interface LocalStore {
    fun trips(month: YearMonth): Flow<List<TripRow>>
    val pendingReviews: Flow<List<TripRow>>        // status != CONFIRMED, 최신 순
    val receiptInbox: Flow<List<ReceiptPayload>>
    suspend fun trip(tripId: String): TripRow?
}
sealed interface ReportBuildResult { data class Built(val file: ReportFile); data class Failed(val reason: String) }
interface ReportApi { suspend fun build(month: YearMonth): ReportBuildResult }
sealed interface ConnectionState { NotRegistered; Registered(vehicleLabel, serverUrl, lastSyncAt: Long?); Unreachable(serverUrl) }
sealed interface RegisterResult { Ok; Invalid(reason) }
interface DeviceApi { val connection: StateFlow<ConnectionState>; suspend fun register(serverUrl: String, code: String): RegisterResult }
data class UploadStatus(val enabled: Boolean, val pendingCount: Int, val lastUploadAt: Long?, val lastError: String?)
sealed interface UploadResult { data class Uploaded(val count: Int); data class Failed(val reason: String) }
interface UploadApi { val status: StateFlow<UploadStatus>; suspend fun uploadNow(): UploadResult }
```
- `FakeTripSession`이 `FakeLocalStore`를 받아 종료 시 `PENDING_REVIEW` 행을 넣고, `confirmPurpose`는 현재 운행이든 지난 운행이든 행을 `CONFIRMED`로 바꾼다(앱 A 설계: `TripSessionApi.confirmPurpose`가 모든 운행 처리).
- `FakeLocalStore`는 이번 달 샘플 6건(확정 3, 확인 필요 1, 운전자 미확정 1, 거리 미확정 1)과 영수증 inbox(`FakeReceiptApi.SAMPLE_RECEIPT`)로 시작. 데모 조작 `pushReceipt(payload)`, `ackReceipt(id)`.
- `FakeReportApi`: `build`는 앱 캐시 대신 임시 파일을 만들고 `LocalStore`의 이달 행으로 `ReportFile` 요약(건수·km·검토 수) 계산. `FakeDeviceApi`: 코드 6자리면 Ok. `FakeUploadApi`: 대기 2건.

**Tests:** 종료하면 확인 필요 목록에 1건 추가 · 지난 운행 목적 확정 → CONFIRMED · 월별 조회는 그 달만 · 연결 코드 6자리 아니면 Invalid · 보고서 요약 합계 = 행 합계(거리 미확정 제외).

---

## Task 2: Route 확장·Home 보조 동작·Repository 4종

**Files:** Modify `nav/Route.kt`, `ui/root/FullApp.kt`, `ui/home/*`, `di/AppGraph.kt`, 두 `FlavorGraph.kt`, `TestAppGraph.kt` · Create `data/repository/ReviewRepository.kt`, `ReceiptRepository.kt`, `ReportRepository.kt`, `SettingsRepository.kt` · Test 각 Repository, `NavigatorTest`(추가), `HomeReducerTest`(추가)

**Interfaces:**
```kotlin
sealed interface Route { …ProfilePicker; Home; data class ReviewTrip(val tripId: String); ReceiptQr;
    data class FuelEntry(val receiptId: String? = null); Report; History; Settings }  // ReceiptReview는 D-20으로 FuelEntry에 합쳐짐
// ReviewRepository: fun review(tripId): Flow<TripReview?>; suspend fun confirm(tripId, detail, note, mode); suspend fun destinationCandidates(): List<String>;
//   suspend fun shouldProposePlace(tripId, detail): Boolean; suspend fun registerPlace(tripId, label): SettingsResult; val voiceAvailable: StateFlow<Boolean>; suspend fun listenPurpose(): VoiceResult<VoicePurpose>
// data class TripReview(tripId, startTime, endTime, distanceKm: String?, origin: String?, destination: String?, suggestion: Suggestion?)
// ReceiptRepository: suspend fun issueQr(): QrResult; val inbox: Flow<List<ReceiptPayload>>; fun receipt(id): Flow<ReceiptPayload?>;
//   suspend fun candidates(r): List<TripCandidate>; suspend fun confirm(r, tripId, edits): ConfirmResult; suspend fun saveFuel(tripId, fuel, source): ConfirmResult; val voiceAvailable; suspend fun listenFuel()
// ReportRepository: fun summary(month): Flow<MonthSummary>; suspend fun build(month): ReportBuildResult; suspend fun send(file, to): MailResult; val smtpConfigured: Boolean; fun recipients(): Flow<List<RecipientInfo>>
// SettingsRepository: DeviceApi·UploadApi·SettingsApi·MailApi 묶음 (connection, register, upload status/now, recipients CRUD, users add, autoMonthlySend, syncNow, saveSmtp)
```
- Home: (2026-11-09 D-23으로 재구성 — 스펙 7.2가 기준) 상태 줄 · 주인공(이번 달 요약 / 이번 운행 + 경로) · 알림 목록(목적 확인 → 영수증 → 놓친 운행) · 하단 독([주유 기록 추가] [운행 기록] [⚙] + "직접 시작"/[기록 종료]).
- 운행 종료(PendingReview)가 되면 Home이 `ReviewTrip`으로 바로 연다(스펙 7.3 진입 1번).

**Tests:** 확인 필요 칩 탭 → `ReviewTrip(최근 id)` · PendingReview 진입 시 ReviewTrip push 1회 · FULL 아니면 push 무시(기존).

---

## Task 3: 공용 컴포넌트 3 — ActionCard·SegmentedOption·InputField·NumericKeypad·ConfidenceRow·QrImage

**Files:** Create `ui/design/components/ActionCard.kt`, `Options.kt`, `InputField.kt`, `NumericKeypad.kt`, `ConfidenceRow.kt`, `QrImage.kt` · Modify `docs/design-language.md` 11장(InputField 사양 추가) · Test `ComponentsTest`(추가: 키패드 입력, 잠금 시 InputField 비활성), `QrImageTest`(ZXing 매트릭스 크기)

- `NumericKeypad(value: String, onChange, allowDecimal)`: 3×4, 키 96 dp, 지우기 키. `InputField(label, value, onChange, enabled, keyboardType, secret)`: 높이 ≥ 76 dp, `outlineStrong` 테두리, 비활성 문구.
- `QrImage(content, sizeDp)`: ZXing `QRCodeWriter`로 `ImageBitmap`, 흰 바탕 + 여백 4모듈(다크에서도 흰 바탕).
- `ConfidenceRow(label, value, confidence)`: ≥ 0.95 초록 · 0.75–0.95 노랑 "확인 필요" · < 0.75 빨강 "다시 확인" + 테두리. 탭하면 편집.

---

## Task 4: ReviewTrip — 목적 확인 (F-06, F-08, F-09, F-17)

> 2026-11-09 구현 후 **운행 결과 카드로 바뀌었다**(스펙 D-27, 브랜치 `feat/review-trip`). 아래 원래 계획(확신 추천 · 선택지 · 음성 · 거래처 등록)은 기록으로 남긴다. 지금: `Route.TripResult`, `ui/result/*`, `TripResultRepository`(`classify` + `detailFor`), `DefaultPurposePolicy`(기본 일반 업무), 5초 자동 저장 · [수정하기]/[저장] · [메인 화면으로]. History(Task 7) 행 탭은 `Route.TripResult`로 연다 — 그때 History에서 연 카드는 카운트다운 없이 열지 정한다.

**Files:** Create `ui/review/ReviewUiState.kt`, `ReviewViewModel.kt`, `ReviewScreen.kt` · Test `ReviewReducerTest`, `ReviewViewModelTest`, `ReviewScreenTest`(조작 예산), `ReviewScreenshotTest`

- 스펙 7.3. 확신(≥ 0.8)이면 큰 추천 버튼 1개 → 1탭 확정 → Home. 아니면 선택지 3그룹(일반 업무용 5 · 출퇴근 2 · 비업무 1) + "나중에". 마이크(음성 가능 시) → 정적 "듣고 있어요" → 인식 원문 + 매칭 목적 선택 상태 → [저장]. 확정 후 `shouldProposePlace`면 "이곳을 거래처로 등록할까요?" 시트(라벨 프리셋 = 목적지명) → [등록]/[아니요].
- 목적지 없음: 최근 목적지 칩(최대 3) 선택.
- WIDE: 좌 요약 / 우 추천·선택지. NARROW·TALL: 세로.

**Tests:** 확신 추천 1탭 → confirm(CLIENT_VISIT, TAP) + Home 복귀 · 확신 낮으면 추천 버튼 없음 · 음성 인식 → 선택 상태 + note · 거래처 제안 시트 노출 조건 · "나중에" → Home, 상태 유지.

---

## Task 5: 주유 기록 — QR 기본 + 입력 창 하나 (F-30, F-19, F-18, D-20)

> 2026-11-09 사용자 결정으로 바뀌었다(스펙 D-20). 영수증 확인(ReceiptReview)과 주유 입력(FuelEntry)을 **입력 창 하나**로 합쳤다. `Route.ReceiptReview`는 없고 `Route.FuelEntry(receiptId: String? = null)`이다(Task 2 정리 커밋에서 반영됨).

**Files:** Create `ui/receipt/ReceiptQrScreen.kt`(+VM/State), `ui/receipt/FuelEntryScreen.kt`(+VM/State, `FuelDraft`·필드 행), `data/repository/ReceiptRepository.kt`에 `add(draft: FuelDraft): ConfirmResult` 추가(출처별 `confirm`/`saveFuel` 분기는 여기 한 곳) · Test reducer·VM·screen·screenshot 각각

- ReceiptQr(스펙 7.4): QR → 작은 글씨 안내 "스마트폰으로 QR 코드를 인식한 뒤 안내에 따라 영수증을 업로드해주세요"(`receipt_qr_hint`) → 남은 시간 → **[직접 입력하기]**(`receipt_manual_entry`, `SecondaryButton`) → `FuelEntry(null)`. inbox에 새 영수증이 오면 `navigator.replaceTop(FuelEntry(receiptId))`(QR을 백스택에서 대체 — `replaceTop`은 이 작업에서 Navigator에 추가, 맨 위 한 칸만 바꾸고 FULL이 아니면 무시). 만료 → [새 QR]. 실패 → 문구 + [다시 시도], 직접 입력은 그대로.
- FuelEntry(스펙 7.5): 공통 필드(금액 필수·리터·단가·연결 운행) + 영수증이면 썸네일·상호·일시·유종·할인·카드 끝 4자리·`ConfidenceRow`·검산 배지. 맨 아래 **[추가하기]**(`fuel_add_confirm`). 영수증 자동 조건(전부 초록·검산 통과·후보 1개)이면 후보 선택 상태로 열린다. 숫자 칸 탭 → `NumericKeypad` 시트. 마이크 없음.
- Home: [주유 기록 추가] → `ReceiptQr`. Home에는 영수증 알림이 없다(스펙 D-26). 대신 ReceiptQr이 열릴 때 inbox에 아직 확정하지 않은 영수증이 있으면 QR을 그리지 않고 곧바로 `replaceTop(FuelEntry(가장 최근 receiptId))` — 열려 있는 동안 새로 도착한 경우와 같은 경로 하나로 처리한다(스펙 7.4 진입).

**Tests:** QR 화면 [직접 입력하기] 1탭 → 빈 입력 창 · 영수증 도착 시 입력 창이 OCR 값으로 채워져 QR을 대체 · 검산 실패 시 [추가하기] 비활성 · 전부 초록이면 [추가하기] 1탭 · 리터 수정 후 추가 시 edits 반영 · OCR 실패 시 금액만으로 추가 · 직접 입력은 `saveFuel`, 영수증은 `confirm`으로 간다(Repository 테스트) · QR 만료 → [새 QR] · 직접 입력 금액 없으면 비활성.

---

## Task 6: 운행기록부·보내기 (F-21, F-22)

> 2026-11-10 구현(브랜치 `feat/report`): 스펙 7.7이 지금 모습의 기준. 화면 이름 "운행기록부 내보내기". 남은 것: 검토 필요 → History 필터, 오프라인 대기열(계약에 없음).

> 2026-11-09(D-23): Home에는 기록부 버튼이 없다. History(Task 7) 맨 위 [운행기록부 만들기·보내기]로 들어온다.

**Files:** Create `ui/report/ReportUiState.kt`, `ReportViewModel.kt`, `ReportScreen.kt` · Test reducer·VM·screen(조작 예산 2탭)·screenshot

- 스펙 7.7. `◀ 2026년 11월 ▶`(미래 비활성) · 요약 카드(건수 · km · 업무 비율 · 검토 N건) · [운행기록부 만들기] → [보내기](수신자 기본 전체 선택, 체크 해제 가능) · 결과 문구(MailError별) · SMTP 미설정이면 [설정에서 메일 계정 연결] · 사내 서버 연결 시 안내 한 줄.

**Tests:** 만들기→보내기 2탭 · 미래 달 이동 불가 · SMTP 미설정 안내 · 실패 사유 문구 매핑.

---

## Task 7: 이력 (F-12)

> 2026-11-10 구현(브랜치 `feat/history`): 스펙 7.8이 지금 모습의 기준. 줄 탭은 운행 결과 카드 **사후 확인 모드**(카운트다운 없음, D-29), 기록부 버튼 이름은 [운행기록부 내보내기].

**Files:** Create `ui/history/*` · Test reducer·screen·screenshot

- 스펙 7.8. Home 독 [운행 기록]으로 들어온다(D-23). 맨 위 **[운행기록부 만들기·보내기]** → Task 6 Report. 월 이동 · 상단 합계(건수·km, 거리 미확정 제외) · 행: 날짜·시각 · 운전자 · 출발 → 도착 · 거리 · 목적 · 상태 배지 · 사내 서버 전송 배지(전송 켜졌을 때만). 확인 필요 행 탭 → ReviewTrip.

---

## Task 8: 설정 (F-24, F-01, 8장)

> 2026-11-10 구현(브랜치 `feat/settings`): 스펙 7.9가 지금 모습의 기준. 8개 섹션 모두, 키보드 규제 연결 포함.

**Files:** Create `ui/settings/*`(섹션별 파일) · Test VM·screen·screenshot

- 스펙 7.9 섹션 8개. WIDE: 좌 섹션 목록 / 우 상세. NARROW·TALL: 목록 → 상세(내부 상태로 전환, Route는 하나).
- 차량 연결: 상태 + [연결 코드 입력](NumericKeypad 6자리). 사내 서버: 주소(InputField, 프리셋 = 마지막 주소) + 연결 상태 + 대기 N건·마지막 전송 + [지금 전송]. 메일: 호스트·포트·아이디·앱 비밀번호(secret) + [연결 확인]. 수신자: 서버 배포분 잠금 아이콘 + 직접 추가분 삭제(확인 1회) + [추가]. 직원: 목록 + [추가]. 자동화: 스위치. 동기화: [지금 동기화] + 결과. 정보: 버전 · 오픈소스 라이선스(Asta Sans OFL) · demo 표시.

**Tests:** 키보드 규제 시 입력 비활성 · 연결 코드 6자리 아니면 Invalid 문구 · 서버 배포 수신자 삭제 불가 · 지금 전송 결과 표시.

---

## Task 9: 마무리 게이트

- `ktlintFormat` → `ktlintCheck testDemoDebugUnitTest assembleDemoDebug assembleVehicleDebug` → `recordRoborazziDemoDebug` → 화면별 스크린샷 자체 리뷰(디자인 언어 13장) → 수정 → `docs/design-gate-2026-11-11.md`에 새 화면 행·달라진 점 추가 → 커밋.
