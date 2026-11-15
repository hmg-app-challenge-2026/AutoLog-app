package kr.htaxlog.vehicle.di

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kr.htaxlog.vehicle.BuildConfig
import kr.htaxlog.vehicle.common.AppClock
import kr.htaxlog.vehicle.data.fake.FakeDeviceApi
import kr.htaxlog.vehicle.data.fake.FakeLocalStore
import kr.htaxlog.vehicle.data.fake.FakeReportApi
import kr.htaxlog.vehicle.data.fake.FakeTripSession
import kr.htaxlog.vehicle.data.fake.FakeUploadApi
import kr.htaxlog.vehicle.data.repository.DefaultContextRepository
import kr.htaxlog.vehicle.data.repository.DefaultProfileRepository
import kr.htaxlog.vehicle.data.repository.DefaultReceiptRepository
import kr.htaxlog.vehicle.data.repository.DefaultReportRepository
import kr.htaxlog.vehicle.data.repository.DefaultSessionRepository
import kr.htaxlog.vehicle.data.repository.DefaultSettingsRepository
import kr.htaxlog.vehicle.data.repository.DefaultTripRepository
import kr.htaxlog.vehicle.data.repository.DefaultTripResultRepository
import kr.htaxlog.vehicle.data.usage.CarUxSource
import kr.htaxlog.vehicle.data.usage.DefaultVehicleUsageRepository
import kr.htaxlog.vehicle.fake.FakeContext
import kr.htaxlog.vehicle.fake.FakeMailApi
import kr.htaxlog.vehicle.fake.FakePurposeEngine
import kr.htaxlog.vehicle.fake.FakeReceiptApi
import kr.htaxlog.vehicle.fake.FakeSettingsApi
import kr.htaxlog.vehicle.fake.FakeVoiceApi
import kr.htaxlog.vehicle.nav.Navigator
import kr.htaxlog.vehicle.nav.Route

/** 그래프가 쓰는 가짜 원천들. 데모 패널·테스트가 직접 조작할 수 있게 노출한다. */
class FakeSources(scope: CoroutineScope, clock: AppClock) {
    val store = FakeLocalStore(clock)
    val trip = FakeTripSession(scope, clock, store = store)
    val purpose = FakePurposeEngine()
    val settingsApi = FakeSettingsApi()
    val context = FakeContext()
    val voice = FakeVoiceApi()
    val receiptApi = FakeReceiptApi()
    val mail = FakeMailApi()
    val report = FakeReportApi(store, clock)
    val device = FakeDeviceApi(clock)
    val upload = FakeUploadApi(clock)
}

/**
 * 가짜 원천으로 조립한 그래프(스펙 6.7) — demo flavor와 UI 테스트가 쓴다. 앱 A 계약은 프론트 `data/fake`(데모 조작 — 거리 늘리기·
 * 놓친 운행·복구 — 가 있는 가짜), 앱 B 계약은 Backend `fake/`. 실제 앱(vehicle flavor)은 [RealAppGraph]가 `HTaxLog.create`로 조립한다.
 */
open class FakeAppGraph(
    scope: CoroutineScope,
    override val clock: AppClock,
    carUx: CarUxSource,
    start: Route = Route.ProfilePicker,
    vehicleId: String = "veh-demo",
    relayUrl: String = BuildConfig.RELAY_BASE_URL,
    overlay: ((FakeSources) -> @Composable () -> Unit)? = null,
) : AppGraph {
    val sources = FakeSources(scope, clock)
    override val navigator = Navigator(start)
    override val session = DefaultSessionRepository(sources.trip)
    override val usage = DefaultVehicleUsageRepository(sources.trip.motion, carUx.state, scope)
    override val profile =
        DefaultProfileRepository(sources.purpose, sources.settingsApi, sources.trip.currentDriver, { vehicleId }, clock)
    override val context = DefaultContextRepository(sources.context)
    override val trips = DefaultTripRepository(sources.store, sources.settingsApi)
    override val tripResults =
        DefaultTripResultRepository(sources.store, sources.trip, sources.purpose, clock) { vehicleId }
    override val receipts =
        DefaultReceiptRepository(sources.receiptApi, sources.store, sources.voice, clock) {
            sources.store.ackReceipt(it)
        }
    override val reports = DefaultReportRepository(sources.report, sources.mail, sources.settingsApi)
    override val settings =
        DefaultSettingsRepository(sources.device, sources.upload, sources.settingsApi, sources.mail, scope, relayUrl)
    override val demoOverlay: (@Composable () -> Unit)? = overlay?.invoke(sources)
}
