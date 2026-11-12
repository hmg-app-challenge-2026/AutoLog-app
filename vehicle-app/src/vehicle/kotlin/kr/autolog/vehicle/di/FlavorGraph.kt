package kr.autolog.vehicle.di

import android.Manifest
import android.app.Application
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.AutoLog
import kr.autolog.vehicle.data.usage.AndroidCarUxSource
import kr.autolog.vehicle.overlay.RecordingPill

/**
 * 실차·Pleos 에뮬레이터용 — 백엔드 실구현(`AutoLog.create`)과 실제 CarUxRestrictions로 조립한다. 목업 데이터 없음.
 * `AutoLog.create`는 앱이 화면에 있을 때(Activity `onCreate`) 불러야 포그라운드 서비스를 시작할 수 있다 — `MainActivity`가
 * [runtimePermissions]를 받은 뒤 처음으로 그래프를 꺼낼 때 이 함수가 불린다(`AutoLogApp.graph`, 백엔드 `AutoLog` 주석).
 */
fun createAppGraph(app: Application): AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val carUx = AndroidCarUxSource(app).also { it.start() }
    val components = AutoLog.create(app)
    val pill = RecordingPill(app, components.session, AppClock.System)
    return RealAppGraph(components, carUx, scope, overlay = pill)
}

/**
 * 조립 전에 받을 런타임 권한(백엔드 `AutoLog` 주석·`vehicle-core` 매니페스트): 알림(Android 13+) · Pleos 좌표 · 차속.
 * 거절해도 앱은 뜬다 — 그 기능만 빠진다(알림 안 보임, 출발·도착 좌표 없음, 차속 없이 계기판만으로 주행·정차 판단).
 * 운행 기록·거리는 그대로. 조립(`AutoLog.create`) 때 차속 리스너를 등록하므로 그 전에 받아야 한다.
 */
fun runtimePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    add(FUSED_LOCATION)
    add(CAR_SPEED)
}

/** `ai.pleos.playground.location.fused.constant.PleosPermission.FUSED_LOCATION`(화면은 ai.pleos.*를 import하지 않는다). */
private const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"

/** AAOS `android.car.Car.PERMISSION_SPEED` — 차속 `PERF_VEHICLE_SPEED`(백엔드 `CarPropertyGateway.speedKph`, `aedb43e`). 런타임 권한. */
private const val CAR_SPEED = "android.car.permission.CAR_SPEED"
