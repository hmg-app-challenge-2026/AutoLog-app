package kr.autolog.vehicle.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kr.autolog.vehicle.nav.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class RouteViewModelStoresTest {
    private class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private val factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Probe() as T
        }

    private fun RouteViewModelStores.probe(route: Route): Probe =
        ViewModelProvider.create(ownerFor(route), factory)[Probe::class]

    @Test
    fun `스택에 남은 화면은 같은 ViewModel, 빠진 화면은 지워지고 다시 오면 새로 만든다`() {
        val stores = RouteViewModelStores()
        val home = stores.probe(Route.Home)
        val review = stores.probe(Route.TripResult("t-1"))

        stores.retainOnly(listOf(Route.Home))

        assertSame(home, stores.probe(Route.Home))
        assertFalse(home.cleared)
        assertTrue(review.cleared)
        assertNotSame(review, stores.probe(Route.TripResult("t-1")))
    }
}
