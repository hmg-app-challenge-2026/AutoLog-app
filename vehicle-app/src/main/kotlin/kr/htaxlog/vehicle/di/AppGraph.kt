package kr.htaxlog.vehicle.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kr.htaxlog.vehicle.common.AppClock
import kr.htaxlog.vehicle.data.repository.ContextRepository
import kr.htaxlog.vehicle.data.repository.ProfileRepository
import kr.htaxlog.vehicle.data.repository.ReceiptRepository
import kr.htaxlog.vehicle.data.repository.ReportRepository
import kr.htaxlog.vehicle.data.repository.SessionRepository
import kr.htaxlog.vehicle.data.repository.SettingsRepository
import kr.htaxlog.vehicle.data.repository.TripRepository
import kr.htaxlog.vehicle.data.repository.TripResultRepository
import kr.htaxlog.vehicle.data.usage.AppReturnGateway
import kr.htaxlog.vehicle.data.usage.VehicleUsageRepository
import kr.htaxlog.vehicle.nav.Navigator
import kr.htaxlog.vehicle.overlay.RecordingOverlay

/** 수동 DI (스펙 D-05·6.7). flavor마다 createAppGraph()가 조립한다. */
interface AppGraph {
    val clock: AppClock
    val navigator: Navigator
    val session: SessionRepository
    val usage: VehicleUsageRepository
    val profile: ProfileRepository
    val context: ContextRepository
    val trips: TripRepository
    val tripResults: TripResultRepository
    val receipts: ReceiptRepository
    val reports: ReportRepository
    val settings: SettingsRepository
    val appReturn: AppReturnGateway get() = AppReturnGateway.Demo

    /** 운행 기록 알약과 그 권한(스펙 6.11). 실제 앱만 있다 — 데모·테스트는 [RecordingOverlay.None]. */
    val overlay: RecordingOverlay get() = RecordingOverlay.None

    /**
     * HTaxLog 화면이 보이는가(`MainActivity` `onStart` = true, `onStop` = false). 알약(보이면 숨김, 스펙 6.11)과
     * 앱 A 운행 알림(보이면 헤즈업 없이 조용히 — 결과 카드와 겹치지 않게, D-37)이 같은 신호를 쓴다.
     */
    fun setAppVisible(visible: Boolean) = overlay.setAppVisible(visible)

    /** demo flavor 데모 패널. vehicle flavor는 null. */
    val demoOverlay: (@Composable () -> Unit)?
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph가 제공되지 않았습니다") }
