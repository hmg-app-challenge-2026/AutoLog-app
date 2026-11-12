package kr.autolog.vehicle.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kr.autolog.vehicle.nav.Route

/**
 * 백스택 화면마다 ViewModel 저장소 하나(Navigation 라이브러리의 NavBackStackEntry 역할, 스펙 6.10).
 * 화면이 스택에서 빠지면([retainOnly]) 그 화면의 ViewModel을 지운다 — 다시 들어오면 새 상태로 시작한다
 * (예: 목적 확인을 끝내고 같은 운행을 이력에서 다시 열 때 "저장 중"·팝업이 남아 있지 않게).
 * Activity 범위 ViewModel 안에 두어 창 크기 변경(분할 화면 전환) 같은 재생성에도 화면 상태가 유지된다.
 */
internal class RouteViewModelStores : ViewModel() {
    private val stores = mutableMapOf<Route, ViewModelStore>()

    fun ownerFor(route: Route): ViewModelStoreOwner {
        val store = stores.getOrPut(route) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    /** [stack]에 없는 화면의 ViewModel을 지운다. */
    fun retainOnly(stack: List<Route>) {
        val keep = stack.toSet()
        stores.keys.filterNot { it in keep }.forEach { stores.remove(it)?.clear() }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}
