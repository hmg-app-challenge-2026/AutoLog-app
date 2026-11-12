package kr.autolog.vehicle.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.autolog.vehicle.data.AutoLogDb
import kr.autolog.vehicle.data.DeviceRepository
import kr.autolog.vehicle.data.EncryptedDeviceStore
import kr.autolog.vehicle.data.InboxSync
import kr.autolog.vehicle.data.InboxSyncWorker
import kr.autolog.vehicle.data.RelayHttp
import kr.autolog.vehicle.data.RelayResult
import kr.autolog.vehicle.data.RoomDomainStore
import kr.autolog.vehicle.data.RoomLocalStore
import kr.autolog.vehicle.data.RoomReportSource
import kr.autolog.vehicle.domain.DefaultPurposeEngine
import kr.autolog.vehicle.domain.ReceiptService
import kr.autolog.vehicle.domain.SettingsService
import kr.autolog.vehicle.location.LocationGateway
import kr.autolog.vehicle.mail.EncryptedSmtpCredentialStore
import kr.autolog.vehicle.mail.MailService
import kr.autolog.vehicle.mail.PrefsAutoSendPreference
import kr.autolog.vehicle.navi.NaviGateway
import kr.autolog.vehicle.report.MonthlyReportWorker
import kr.autolog.vehicle.report.MonthlySender
import kr.autolog.vehicle.report.PrefsMonthlySentStore
import kr.autolog.vehicle.report.ReportBuilder
import kr.autolog.vehicle.session.NotificationPresence
import kr.autolog.vehicle.session.TripRecordingService
import kr.autolog.vehicle.session.TripSessionManager
import kr.autolog.vehicle.speech.SpeechGateway
import kr.autolog.vehicle.speech.VoiceService
import kr.autolog.vehicle.vehicle.VehicleGateway
import java.io.File

/** 화면이 쓰는 모든 계약 구현. [AutoLog.create]가 조립한다. 프론트는 이 인터페이스들만 쓴다. */
class AutoLogComponents(
    /** 앱 A: 운행 세션 (F-02–05, F-10, F-13). */
    val session: TripSessionApi,
    /** 앱 A: 운행 기록 조회 (F-12, F-19). */
    val local: LocalStore,
    /** 앱 A: 운행기록부 XLSX (F-21, F-23). */
    val report: ReportApi,
    /** 앱 A: 서버 연결 (F-01). */
    val device: DeviceApi,
    /** 앱 B: 내비 목적지·좌표 (F-06, F-07). */
    val context: ContextApi,
    /** 앱 B: 목적·운전자 추천 (F-08, F-15, F-17, F-20). */
    val purpose: PurposeEngine,
    /** 앱 B: 음성 입력 (F-09, F-18). */
    val voice: VoiceApi,
    /** 앱 B: 영수증 (F-19, F-30). */
    val receipts: ReceiptApi,
    /** 앱 B: 메일 (F-22). */
    val mail: MailApi,
    /** 앱 B: 설정 (F-24). */
    val settings: SettingsApi,
    /** 앱 A: 화면이 보이는지 — 보이는 동안 알림은 조용히 (D-37). */
    val presence: AppPresenceApi = NotificationPresence,
)

/**
 * 조립 지점 (프론트 R-1). 앱 프로세스당 하나를 만든다.
 * 프론트: Activity `onCreate`(앱이 화면에 있을 때)에서 `AutoLog.create(this)`를 부른다 — 포그라운드 서비스 시작이 허용되는 시점이다.
 * 하는 일: Room 열기 → SDK 게이트웨이 연결 → 세션 복구·차량 신호 구독 → 기록 서비스 → inbox 주기 작업.
 * 프론트는 그 전에 런타임 권한을 요청한다: `POST_NOTIFICATIONS`(알림), `pleos.car.permission.FUSED_LOCATION`(좌표).
 * 권한이 없으면 그 기능만 빠지고(좌표 없음·알림 안 보임) 운행 기록·거리는 그대로 된다.
 */
object AutoLog {
    private const val TAG = "AutoLog/core"

    @Volatile private var instance: AutoLogComponents? = null

    fun create(context: Context): AutoLogComponents = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(app: Context): AutoLogComponents {
        val log: (String) -> Unit = { Log.i(TAG, it) }
        // SDK 하나가 실패해도(권한 없음 등) 앱 프로세스를 죽이지 않는다. 각자 기능만 빠진다.
        val guard = CoroutineExceptionHandler { _, e -> Log.w(TAG, "background failure: ${e.message}") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + guard)
        val db = AutoLogDb.open(app)

        // 서버 연결·inbox (앱 A)
        val device = DeviceRepository(EncryptedDeviceStore(app), RelayHttp(), log = log)
        val inbox = InboxSync(device, db.referenceDao(), db.expenseDao(), db.inboxDao(), log = log)
        device.onRegistered = { inbox.syncNow() }
        val store = RoomDomainStore(db.tripDao(), db.referenceDao(), db.expenseDao(), ackRemote = { id ->
            device.call { url, token -> ack(url, token, id) } is RelayResult.Ok
        })

        // SDK 게이트웨이
        val vehicle = VehicleGateway(app).also {
            runCatching { it.start() }.onFailure { e -> log("vehicle start: ${e.message}") }
        }
        val navi = NaviGateway(app)
        val location = LocationGateway(app, scope)
        val speech = SpeechGateway(app)
        scope.launch { runCatching { navi.start() }.onFailure { log("navi start: ${it.message}") } }
        runCatching { location.start() }.onFailure { log("location start: ${it.message}") }
        scope.launch { speech.start() }

        // 앱 B 로직
        val ctx = GatewayContext(navi, location, scope)
        val purpose = DefaultPurposeEngine(store, log = log)

        // 앱 A 세션·조회·운행기록부
        val session = TripSessionManager(
            vehicle = vehicle,
            dao = db.tripDao(),
            context = ctx,
            purpose = purpose,
            scope = scope,
            vehicleId = { device.vehicleId ?: TripSessionManager.UNREGISTERED_VEHICLE },
            log = { Log.i("AutoLog/session", it) },
        )
        val report = ReportBuilder(
            RoomReportSource(db.tripDao(), db.referenceDao(), db.expenseDao()) { device.vehicleId },
            outputDir = { File(app.filesDir, "reports") },
            plateNo = { device.plateNo },
            log = log,
        )

        val settings = SettingsService(store, inbox, PrefsAutoSendPreference(app), log = log)
        val mail = MailService(EncryptedSmtpCredentialStore(app))
        val components = AutoLogComponents(
            session = session,
            local = RoomLocalStore(db.tripDao(), db.expenseDao()),
            report = report,
            device = device,
            context = ctx,
            purpose = purpose,
            voice = VoiceService(speech, store, log = log),
            receipts = ReceiptService(store, inbox, log = log, scope = scope),
            mail = mail,
            settings = settings,
        )

        session.start()
        runCatching { TripRecordingService.start(app, session) }.onFailure { log("service start: ${it.message}") }
        MonthlyReportWorker.host = MonthlySender(settings, report, mail, PrefsMonthlySentStore(app), log = log)
        MonthlyReportWorker.schedule(app)
        InboxSyncWorker.host = inbox
        InboxSyncWorker.schedule(app)
        InboxSyncWorker.runNow(app)
        // 정차(운행 종료) 때마다 inbox를 한 번 받는다(F-11 "정차 전환 시")
        scope.launch {
            session.state.collect { if (it is SessionState.PendingReview) InboxSyncWorker.runNow(app) }
        }
        log("AutoLog ready")
        return components
    }
}

/**
 * [ContextApi] 실구현 — NaviGateway(목적지·도착·최근 목적지) + LocationGateway(좌표). 앱 B 게이트웨이를 계약 모양으로 잇는 어댑터.
 * 조립에만 쓰는 연결 코드라 여기 둔다. 앱 B가 navi/ 쪽으로 옮겨도 된다.
 */
private class GatewayContext(
    private val navi: NaviGateway,
    private val location: LocationGateway,
    scope: CoroutineScope,
) : ContextApi {
    override val destination: StateFlow<DestinationInfo?> = navi.destination
        .map { d -> d?.let { DestinationInfo(it.label, it.address, it.lat, it.lng, it.poiId) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val latestFix: StateFlow<LocationFix?> = location.latestFix

    override val arrivals: Flow<ArrivalInfo> = navi.arrivals.map { ArrivalInfo(it.label, it.address, it.at) }

    override suspend fun freshFix(): LocationFix? = location.snapshot()

    override suspend fun currentNaviLocation(): NaviCurrentLocation? = runCatching {
        navi.currentLocation()?.let { NaviCurrentLocation(it.lat, it.lng, it.address) }
    }.getOrNull()

    override suspend fun recentDestinations(): List<DestinationInfo> = runCatching {
        navi.recentDestinations().map { DestinationInfo(it.label, it.address, it.lat, it.lng) }
    }.getOrDefault(emptyList())
}
