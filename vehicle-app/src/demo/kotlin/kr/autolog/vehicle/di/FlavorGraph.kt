package kr.autolog.vehicle.di

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.usage.StaticCarUxSource
import kr.autolog.vehicle.demo.DemoPanelOverlay
import kr.autolog.vehicle.demo.DemoScenario

/** 전부 Fake (스펙 6.7). 일반 에뮬레이터·시연 영상 백업용. 오른쪽 위를 길게 누르면 데모 패널. */
fun createAppGraph(app: Application): AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock = AppClock.System
    return FakeAppGraph(scope, clock, StaticCarUxSource()) { sources ->
        val scenario = DemoScenario(sources.trip, scope, clock)
        ({ DemoPanelOverlay(scenario) })
    }
}

/** 데모는 실제 SDK·알림을 쓰지 않으므로 받을 런타임 권한이 없다. */
fun runtimePermissions(): List<String> = emptyList()
