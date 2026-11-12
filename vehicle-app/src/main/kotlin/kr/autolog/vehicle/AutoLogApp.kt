package kr.autolog.vehicle

import android.app.Application
import kr.autolog.vehicle.di.AppGraph
import kr.autolog.vehicle.di.createAppGraph

/**
 * 그래프는 **처음 꺼낼 때** 만든다(지연). 실제 앱의 조립(`AutoLog.create`)은 포그라운드 서비스를 시작하므로 앱이 화면에 있을 때
 * — `MainActivity`가 런타임 권한을 받은 뒤 — 불려야 한다. Application.onCreate에서 만들면 서비스 시작이 거절될 수 있다.
 * 부팅 뒤에는 백엔드 `BootReceiver`가 먼저 `AutoLog.create`를 불러 두고, 여기서는 같은 인스턴스를 받는다.
 */
class AutoLogApp : Application() {
    val graph: AppGraph by lazy { createAppGraph(this) }
}
