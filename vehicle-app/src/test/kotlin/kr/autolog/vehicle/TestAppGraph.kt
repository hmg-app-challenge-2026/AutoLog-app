package kr.autolog.vehicle

import kotlinx.coroutines.CoroutineScope
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.usage.StaticCarUxSource
import kr.autolog.vehicle.di.FakeAppGraph
import kr.autolog.vehicle.nav.Route

/** UI 테스트용 그래프. 데모 패널이 없다. [carUx]·[trip]·[store]를 테스트가 직접 조작한다. */
class TestAppGraph private constructor(
    scope: CoroutineScope,
    clock: AppClock,
    start: Route,
    val carUx: StaticCarUxSource,
    relayUrl: String,
) : FakeAppGraph(scope, clock, carUx, start, vehicleId = "veh-test", relayUrl = relayUrl) {
    constructor(
        scope: CoroutineScope,
        clock: AppClock = AppClock.System,
        start: Route = Route.ProfilePicker,
        relayUrl: String = "https://relay.example",
    ) : this(scope, clock, start, StaticCarUxSource(), relayUrl)

    val trip get() = sources.trip
    val store get() = sources.store
}
