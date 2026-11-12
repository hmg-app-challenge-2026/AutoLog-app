package kr.autolog.vehicle.ui.profile

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.data.repository.AddDriverResult
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePickerViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `타일 1탭으로 운전자를 정하고 Home으로 간다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

        vm.onAction(ProfilePickerAction.Select("u-2"))

        assertEquals("u-2", graph.trip.currentDriver.value)
        assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
    }

    @Test
    fun `나중에 선택하면 운전자 없이 Home으로 간다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

        vm.onAction(ProfilePickerAction.Later)

        assertNull(graph.trip.currentDriver.value)
        assertEquals(Route.Home, graph.navigator.current)
    }

    @Test
    fun `배차 운전자가 첫 자리이고 새 운전자 추가 타일이 맨 끝이다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

        vm.uiState.test {
            val loaded = expectMostRecentItem().takeIf { it.loaded } ?: awaitItem()
            assertEquals(PickerTile.Person(loaded.profiles.first()), loaded.tiles.first())
            assertEquals("u-1", loaded.profiles.first().userId)
            assertEquals(PickerTile.Add, loaded.tiles.last())
        }
    }

    @Test
    fun `새 운전자를 추가하면 그 사람으로 바로 시작한다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

        vm.onAction(ProfilePickerAction.OpenAdd)
        vm.onAction(ProfilePickerAction.AddNameChanged("최신입"))
        vm.onAction(ProfilePickerAction.AddDeptChanged("영업2팀"))
        vm.onAction(ProfilePickerAction.SubmitAdd)

        val added = graph.profile.orderedProfiles().first().single { it.name == "최신입" }
        assertEquals(added.userId, graph.trip.currentDriver.value)
        assertEquals("영업2팀", added.dept)
        assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
    }

    @Test
    fun `이름이 비면 추가 버튼이 눌리지 않는다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

        vm.uiState.test {
            skipItems(1)
            vm.onAction(ProfilePickerAction.OpenAdd)
            vm.onAction(ProfilePickerAction.AddNameChanged("  "))
            vm.onAction(ProfilePickerAction.SubmitAdd)

            val form = expectMostRecentItem().addForm
            assertFalse(form!!.canSubmit)
            assertNull(graph.trip.currentDriver.value)
            assertEquals(Route.ProfilePicker, graph.navigator.current)
        }
    }

    @Test
    fun `원천이 거절하면 시트에 사유를 남기고 다시 입력할 수 있다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val rejecting =
            object : ProfileRepository by graph.profile {
                override suspend fun addDriver(name: String, dept: String?) = AddDriverResult.Rejected("중복된 이름입니다")
            }
        val vm = ProfilePickerViewModel(graph.session, rejecting, graph.navigator)

        vm.uiState.test {
            skipItems(1)
            vm.onAction(ProfilePickerAction.OpenAdd)
            vm.onAction(ProfilePickerAction.AddNameChanged("김영업"))
            vm.onAction(ProfilePickerAction.SubmitAdd)
            val failed = expectMostRecentItem().addForm!!
            assertEquals(AddDriverResult.Rejected("중복된 이름입니다"), failed.error)
            assertFalse(failed.saving)

            vm.onAction(ProfilePickerAction.AddNameChanged("김영업2"))
            assertNull(expectMostRecentItem().addForm!!.error)
        }
    }

    @Test
    fun `취소하면 시트만 닫힌다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

        vm.uiState.test {
            skipItems(1)
            vm.onAction(ProfilePickerAction.OpenAdd)
            vm.onAction(ProfilePickerAction.CancelAdd)
            assertNull(expectMostRecentItem().addForm)
            assertEquals(Route.ProfilePicker, graph.navigator.current)
        }
    }
}
