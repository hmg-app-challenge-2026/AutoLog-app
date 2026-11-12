package kr.autolog.vehicle.di

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.AutoLogComponents
import kr.autolog.vehicle.data.repository.DefaultContextRepository
import kr.autolog.vehicle.data.repository.DefaultProfileRepository
import kr.autolog.vehicle.data.repository.DefaultReceiptRepository
import kr.autolog.vehicle.data.repository.DefaultReportRepository
import kr.autolog.vehicle.data.repository.DefaultSessionRepository
import kr.autolog.vehicle.data.repository.DefaultSettingsRepository
import kr.autolog.vehicle.data.repository.DefaultTripRepository
import kr.autolog.vehicle.data.repository.DefaultTripResultRepository
import kr.autolog.vehicle.data.upload.UnavailableUploadApi
import kr.autolog.vehicle.data.usage.CarUxSource
import kr.autolog.vehicle.data.usage.DefaultVehicleUsageRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.overlay.RecordingOverlay

/**
 * 실제 앱 그래프(스펙 6.7, R-1) — 백엔드 조립 지점 `AutoLog.create`가 돌려준 [components](앱 A·B 실구현) 위에 화면 Repository를 얹는다.
 * 목업 데이터는 하나도 없다. 백엔드에 계약이 없는 것만 비워 둔다: 사내 서버 업로드([UnavailableUploadApi], G-04).
 * 연결 지도와 연결하지 못한 것은 `docs/integration/backend-link.md`.
 */
class RealAppGraph(
    components: AutoLogComponents,
    carUx: CarUxSource,
    scope: CoroutineScope,
    override val clock: AppClock = AppClock.System,
    /** 운행 기록 알약(스펙 6.11). vehicle flavor가 `RecordingPill`을 넣는다. */
    override val overlay: RecordingOverlay = RecordingOverlay.None,
) : AppGraph {
    override val navigator = Navigator(Route.ProfilePicker)
    override val session = DefaultSessionRepository(components.session)
    override val usage = DefaultVehicleUsageRepository(components.session.motion, carUx.state, scope)
    override val profile =
        DefaultProfileRepository(
            components.purpose,
            components.settings,
            components.session.currentDriver,
            ::vehicleId,
            clock,
        )
    override val context = DefaultContextRepository(components.context)
    override val trips = DefaultTripRepository(components.local, components.settings)
    override val tripResults =
        DefaultTripResultRepository(
            components.local,
            components.session,
            components.purpose,
            clock,
            vehicleId = ::vehicleId,
        )

    // 실제 ReceiptApi.confirm이 Room 확정·서버 ack까지 한다 — 화면 쪽 ack는 하지 않는다
    override val receipts = DefaultReceiptRepository(components.receipts, components.local, components.voice, clock)
    override val reports = DefaultReportRepository(components.report, components.mail, components.settings)
    override val settings =
        DefaultSettingsRepository(
            components.device,
            UnavailableUploadApi(),
            components.settings,
            components.mail,
            scope,
        )
    override val demoOverlay: (@Composable () -> Unit)? = null

    /**
     * 추천 엔진(배차 순서·목적 추천)에 넘길 차량 id. **계약에 차량 id가 없다**(DeviceApi·ConnectionState는 번호판 라벨만 준다, G-02) —
     * 등록 전 운행이 쓰는 값과 같은 "unregistered"를 넘긴다. 그래서 배차 기반 순서·"배차" 배지·배차 목적 추천은 계약이 열릴 때까지 비어 있다.
     */
    private fun vehicleId(): String = UNKNOWN_VEHICLE

    private companion object {
        /** 백엔드 `TripSessionManager.UNREGISTERED_VEHICLE`과 같은 값(session 패키지는 화면이 import하지 않는다). */
        const val UNKNOWN_VEHICLE = "unregistered"
    }
}
