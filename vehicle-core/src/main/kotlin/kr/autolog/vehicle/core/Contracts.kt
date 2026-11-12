// 앱 A · 앱 B · 프론트의 경계 (기술명세 8.2).
// - 프론트(app/ui)는 이 파일의 인터페이스와 ContractTypes.kt 의 타입만 쓴다.
// - 앱 A·B도 서로의 구현을 직접 참조하지 않고 이 인터페이스로만 쓴다.
// - 시그니처 변경은 contract/ 브랜치 PR로만, A·B 둘 다 승인. 같은 PR에서 fake/ 도 고친다. (CONVENTIONS.md 5장)
// - 여기 함수는 예외를 던지지 않는다. 실패는 반환값(null, 빈 목록, *Result)으로 알린다. (CONVENTIONS.md 7.5)
package kr.autolog.vehicle.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import java.time.YearMonth

// ===== 앱 A 제공 =====
// 출발점: 프론트 초안(AutoLog-Frontend 스펙 6.6, R-2·R-7·R-9·R-10). 구현: session/·data/·report/.

/**
 * 운행 세션 (F-02–05, F-10, F-13, F-16). 세션이 유일한 시계다 — 화면은 [SessionState.StopCandidate.deadlineAt]만 보고
 * 남은 시간을 계산한다. 구현: session/TripSessionManager.
 */
interface TripSessionApi {
    /** 프론트: Home·Peek·자동 종료 안내. 앱 A: 알림. */
    val state: StateFlow<SessionState>

    /** 프론트: 사용 3단계(주차=전체 앱, 정차=Peek, 주행=화면 없음)와 입력 차단(F-13). null = 판정 불가 → 주행으로 본다. */
    val motion: StateFlow<VehicleMotion?>

    /** 프론트: 계기판 누적거리 표시. 못 읽으면 null(추정 금지). */
    val totalOdometerKm: StateFlow<Double?>

    /** 프론트: "기록되지 않은 운행 N km" 배너 (F-10). 없으면 null. */
    val missedTrip: StateFlow<MissedTrip?>

    /** 프론트: 오른쪽 위 운전자 배지. 다음 운행의 운전자. null = 나중에 선택. */
    val currentDriver: StateFlow<String?>

    /** 프론트: 운전자 선택 화면 (F-15). 다음 운행부터 적용하고 recent_users를 갱신한다. */
    suspend fun selectDriver(userId: String?)

    /** 프론트: [운행 시작] (F-02). 이미 기록 중이면 Failed. Odometer를 못 읽어도 시작한다(거리 미확정). */
    suspend fun startSession(): StartResult

    /**
     * 프론트 [기록 종료](주차 중): 5초 종료 대기에 들어가 결과 화면을 연다. 사람이 끝낸 대기라 차가 움직여도 되돌리지 않는다.
     * 마감 때 주차(P)가 아니면 [finalize]처럼 다음 주차까지 자동 시작을 멈춘다 (F-04).
     */
    suspend fun requestStop()

    /**
     * 프론트 [기록 종료](정차·주행 중)·종료 대기 화면·알림: 기다리지 않고 바로 끝낸다 (F-04).
     * 주차(P)가 아닐 때(정차·주행 중) 끝내면 다음 주차 전까지 새 운행을 자동으로 시작하지 않는다 — 신호 대기로는 풀리지 않는다(끝낸 사람의 뜻대로).
     */
    suspend fun finalize()

    /** 프론트·알림: [계속 기록]. StopCandidate → Recording, 정차 기록(Stop)만 남긴다 (F-04). */
    suspend fun resume()

    /**
     * 프론트: 목적 확정 (F-08, F-09). 지금 끝난 운행이든 지난 운행이든 [tripId]로 확정한다.
     * [destinationLabel]은 목적지 없이 끝난 운행에서 사용자가 고른 도착지 이름(F-06). null이면 그대로 둔다.
     */
    suspend fun confirmPurpose(
        tripId: String,
        detail: PurposeDetail,
        note: String?,
        inputMode: InputMode,
        destinationLabel: String? = null,
    )

    /** 프론트: 운전자 미확정 운행에 운전자 지정 (F-20). */
    suspend fun assignDriver(tripId: String, userId: String, source: DriverSource)

    /** 프론트: 놓친 운행 배너 [추가]. 거리 = 계기판 차이, 시각은 추정으로 표시한다 (F-10). */
    suspend fun acceptMissedTrip()

    /** 프론트: 놓친 운행 배너 [무시]. 같은 계기판 값으로는 다시 묻지 않는다 (F-10). */
    suspend fun ignoreMissedTrip()

    /** 프론트: 강제 종료 후 복구 다이얼로그 [기록하기][계속][삭제] (F-10). */
    suspend fun resolveRecovery(choice: RecoveryChoice)
}

/** 화면이 읽는 운행 기록 (F-12). 원본은 Room. 구현: data/RoomLocalStore. */
/**
 * 앱 A: AutoLog 화면이 보이는지 화면이 알린다(Activity `onStart` = true, `onStop` = false — 분할 화면 한 칸도 보이는 것).
 * 보이는 동안엔 운행 알림을 헤즈업(소리)으로 띄우지 않는다 — 화면이 운행 결과 카드로 같은 것을 이미 보여 준다(프론트 스펙 D-37).
 */
interface AppPresenceApi {
    fun setAppVisible(visible: Boolean)
}

interface LocalStore {
    /** 프론트: 이력·운행기록부 화면. [month](Asia/Seoul 기준)에 시작한 운행, 최신 순. */
    fun trips(month: YearMonth): Flow<List<TripRow>>

    /** 프론트: "확인 필요 N건". 목적 미확정(purpose == null)인 끝난 운행, 최신 순. */
    val pendingReviews: Flow<List<TripRow>>

    /** 프론트: Home "최근 운행 기록". 달과 상관없이 최근 [limit]건, 최신 순. */
    fun recent(limit: Int): Flow<List<TripRow>>

    /** 프론트: 영수증 확인 화면 (F-19). inbox로 받았지만 아직 확정·ack하지 않은 영수증. */
    val receiptInbox: Flow<List<ReceiptPayload>>

    /** 프론트: ReviewTrip 진입. 없으면 null. */
    suspend fun trip(tripId: String): TripRow?
}

/** 운행기록부 XLSX (F-21, F-23). 구현: report/ReportBuilder. */
interface ReportApi {
    /** 프론트: [생성]·[보내기] 전. 결과 파일은 앱 B의 [MailApi.send]에 그대로 넘긴다. */
    suspend fun build(month: YearMonth): ReportBuildResult
}

/** 서버 연결 (F-01, F-40). 서버 주소는 런타임 설정. 구현: data/DeviceRepository. */
interface DeviceApi {
    /** 프론트: 설정 화면 연결 상태. */
    val connection: StateFlow<ConnectionState>

    /** 프론트: 설정 화면 [연결]. 서버 주소와 운영 웹에서 받은 연결 코드. 성공하면 inbox를 한 번 받는다. */
    suspend fun register(serverUrl: String, code: String): RegisterResult
}

/**
 * 앱 B 로직(추천·영수증 매칭·설정)이 읽고 쓰는 차 안 데이터. **앱 A가 Room으로 구현**해 B의 구현체에 주입한다.
 * B가 A의 Room 엔티티에 묶이지 않도록 경계 타입(ContractTypes.kt)만 쓴다. 화면(프론트)은 쓰지 않는다.
 */
interface DomainStore {
    /** 직원 목록(서버 배포 + 차 안 입력). 앱 B: 운전자 선택 정렬, 설정 화면. */
    fun users(): Flow<List<UserInfo>>

    /** 최근 운전자(recent_users), 최근 순. 앱 B: 운전자 선택 정렬. */
    suspend fun recentUsers(limit: Int): List<RecentUse>

    /** 등록 장소(서버 배포 + 차 안 등록). 앱 B: 목적 추천, 영수증 매칭. */
    suspend fun places(): List<PlaceInfo>

    /** 이 차의 배차 중 [from]~[to]와 겹치는 것. 앱 B: 목적·운전자 추천. */
    suspend fun schedules(vehicleId: String, from: Long, to: Long): List<ScheduleInfo>

    /** [from]~[to]에 시작한 운행, 시작 시각 순. 앱 B: 직전 운전자, 과거 확정 목적, 영수증 후보. */
    suspend fun trips(from: Long, to: Long): List<TripSummary>

    /** 수신자 목록(서버 배포 + 차 안 입력). 앱 B: 설정·보내기 화면. */
    fun recipients(): Flow<List<RecipientInfo>>

    /** origin=LOCAL로 저장. 같은 이메일이 있으면 덮어쓴다. */
    suspend fun saveRecipient(r: RecipientInfo)

    /** origin=LOCAL인 것만 지운다. 서버 배포분은 지우지 않는다. */
    suspend fun deleteLocalRecipient(email: String)

    /** origin=LOCAL로 저장 (F-24 직원 직접 입력). */
    suspend fun saveUser(u: UserInfo)

    /** origin=LOCAL로 저장 (F-17 차 안 거래처 등록). */
    suspend fun savePlace(p: PlaceInfo)

    /** 이미 같은 sha256의 비용이 저장돼 있는지 (F-19 중복 거부). */
    suspend fun hasReceipt(sha256: String): Boolean

    /** expenses + trip_expense_links 저장. 저장된 expenseId를 돌려준다. */
    suspend fun saveExpense(e: ExpenseDraft): String

    /** 영수증 inbox 항목을 처리 완료로 표시하고 서버에 ack한다 (실패하면 InboxSyncWorker가 다시 ack). */
    suspend fun ackReceipt(receiptId: String)
}

/** 중계 서버 호출 중 앱 B 기능이 쓰는 것. **앱 A가 RelayApi(기기 토큰 보관)로 구현**한다. 예외 대신 결과 값. */
interface RelayClient {
    /** POST /v1/qr-tokens (F-30). */
    suspend fun issueQrToken(): QrResult

    /** inbox를 지금 한 번 가져온다 (F-24 [지금 동기화]). */
    suspend fun syncNow(): SyncResult
}

// ===== 앱 B 제공 =====

/** 내비 목적지와 현재 좌표 (F-06, F-07). 구현: NaviGateway + LocationGateway. */
interface ContextApi {
    /** 지금 내비에 설정된 목적지. 없으면 null. 앱 A: 운행 종료 때 Trip에 저장 / 프론트: 메인 화면 표시. */
    val destination: StateFlow<DestinationInfo?>

    /** 가장 최근 좌표. 오래된 값일 수 있다. 프론트: 위치 표시. */
    val latestFix: StateFlow<LocationFix?>

    /** 내비가 알려 준 도착. 앱 A: 정차 감지 보조 신호 (F-04 "RouteState=도착"). */
    val arrivals: Flow<ArrivalInfo>

    /** 30초 이내 좌표. 없으면 잠깐(최대 3초) 기다리고, 그래도 없으면 null. 앱 A: 출발·도착 스냅샷 (F-07). */
    suspend fun freshFix(): LocationFix?

    /** 내비가 알려 준 현재 위치와 주소. 운행 출발지에 저장한다. */
    suspend fun currentNaviLocation(): NaviCurrentLocation?

    /** 내비 최근 목적지. 실패하면 빈 목록. 프론트: 목적지 없이 끝난 운행의 도착지 후보 (F-06). */
    suspend fun recentDestinations(): List<DestinationInfo>
}

/**
 * 목적·운전자·장소 추천 (F-08, F-15, F-17, F-20). 결과는 추천일 뿐이고 확정은 사용자가 한다.
 * 구현: domain/ 의 PurposeRules, DriverResolver, UserOrdering, PlaceSuggester.
 */
interface PurposeEngine {
    /** 앱 A: 운행 종료(FINALIZING) 때 불러 PendingReview에 담는다. 근거가 없으면 NEEDS_CHECK(신뢰도 낮음). */
    suspend fun suggest(trip: TripFacts): Suggestion

    /** 앱 A: 운전자가 비어 있는 운행에 채울 후보. 근거가 없으면 null — 미확정으로 둔다 (F-20). */
    suspend fun driverCandidate(trip: TripFacts): DriverCandidate?

    /** 프론트: 운전자 선택 화면 (F-15). 배차된 사람 → 최근 운전자 → 이름순. */
    fun orderedUsers(vehicleId: String, now: Long): Flow<List<UserCard>>

    /** 프론트: 목적 확정 직후 "이곳을 거래처로 등록할까요?"를 띄울지 (F-17). */
    suspend fun shouldProposePlace(trip: TripFacts, detail: PurposeDetail): Boolean

    /** 프론트: 위 질문에서 [등록]. 도착 좌표를 거래처(origin=LOCAL)로 저장한다 (F-17). */
    suspend fun registerClientPlace(trip: TripFacts, label: String): SettingsResult

    /**
     * 프론트: 운전자 선택 화면 위 "이전 운행(09:00, 23.4 km) 운전자는?" 카드 (F-20).
     * 최근 7일 안에 끝난 운전자 미확정 운행, 최신 순. 고르면 TripSessionApi.assignDriver(앱 A)로 저장한다.
     */
    suspend fun unassignedTrips(vehicleId: String, now: Long): List<UnassignedTrip>
}

/** 음성 입력 (F-09, F-18). 정차 중에만 부른다. 구현: speech/ (Gleo STT). */
interface VoiceApi {
    /** 프론트: false면 마이크 버튼을 숨긴다 (F-09 "STT 미지원 → 버튼 숨김"). */
    val available: StateFlow<Boolean>

    /** 프론트: 목적 확인 화면의 마이크 버튼. */
    suspend fun listenPurpose(): VoiceResult<VoicePurpose>

    /** 프론트: 주유 기록의 마이크 버튼. */
    suspend fun listenFuel(): VoiceResult<VoiceFuel>
}

/** 영수증 (F-19, F-30). 구현: domain/ReceiptMatcher + 서버 QR 토큰 API. */
interface ReceiptApi {
    /** 프론트: [영수증 저장] 버튼 → 폰으로 찍을 QR 표시. */
    suspend fun issueQr(): QrResult

    /** 프론트: 영수증 확인 화면의 연결 운행 후보. 최대 3개, 촬영 ±2시간, 주유소 정차 우선. */
    suspend fun candidates(receipt: ReceiptPayload): List<TripCandidate>

    /** 프론트: [확정]. 고친 값으로 비용을 저장하고 운행에 연결한 뒤 inbox를 ack한다. [tripId]가 null이면 운행 미연결. */
    suspend fun confirm(receipt: ReceiptPayload, tripId: String?, edits: ReceiptFields): ConfirmResult

    /**
     * 프론트: 주유 기록 저장 (F-18). 음성([VoiceApi.listenFuel]) 또는 숫자 탭으로 받은 값. 영수증 없음.
     * [tripId]는 지금(또는 방금 끝난) 운행. 금액이 없거나 검산이 틀리면 Rejected.
     */
    suspend fun saveFuel(tripId: String?, fuel: VoiceFuel, source: ExpenseSource, at: Long): ConfirmResult
}

/** 운행기록부 메일 (F-22, F-24). 구현: mail/ (SMTP). */
interface MailApi {
    /** 프론트: 보내기 버튼. 앱 A가 만든 XLSX를 첨부해 보낸다. */
    suspend fun send(report: ReportFile, to: List<String>): MailResult

    /** 프론트: 설정 화면. 접속을 한 번 확인한 뒤 비밀번호는 암호화해 저장한다. */
    suspend fun saveSmtp(config: SmtpConfig): MailResult

    /** 프론트: 보내기 버튼 활성화 여부. */
    fun smtpConfigured(): Boolean
}

/** 설정 화면 (F-24). 메일 계정은 [MailApi], 차량 연결은 DeviceApi(앱 A). */
interface SettingsApi {
    /** 프론트: 수신자 목록. 서버 배포분 먼저, 같은 이메일은 서버 배포분 하나만. */
    fun recipients(): Flow<List<RecipientInfo>>

    /** 프론트: 수신자 직접 추가. 이메일 형식이 틀리면 Invalid. */
    suspend fun addRecipient(email: String, name: String?): SettingsResult

    /** 프론트: 직접 추가한 수신자 삭제. 서버 배포분은 Invalid. */
    suspend fun removeRecipient(email: String): SettingsResult

    /** 프론트: 직원 목록. 이름순. */
    fun users(): Flow<List<UserInfo>>

    /** 프론트: 직원 직접 추가 (서버 없이도 운전자 선택에 뜬다). */
    suspend fun addUser(name: String, dept: String?, email: String?): SettingsResult

    /** 프론트: 월말 운행기록부 자동 생성·발송 스위치. 앱 A: 월말 작업이 이 값을 보고 실행한다. */
    val autoMonthlySend: StateFlow<Boolean>

    suspend fun setAutoMonthlySend(enabled: Boolean)

    /** 프론트: [지금 동기화]. */
    suspend fun syncNow(): SyncResult
}

// ===== 앱 B(서버) 제공, 앱 A(InboxSyncWorker) 소비 — shared 모듈 =====
// POST /v1/devices · GET /v1/inbox · POST /v1/inbox/{id}/ack · POST /v1/qr-tokens   (기술명세 4.3)
