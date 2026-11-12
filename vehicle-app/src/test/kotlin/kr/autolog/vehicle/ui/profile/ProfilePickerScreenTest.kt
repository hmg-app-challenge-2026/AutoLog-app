package kr.autolog.vehicle.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.AddDriverResult
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.ProfileBadge
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val profiles =
    listOf(
        DriverProfile("u-1", "김영업", "영업1팀", ProfileBadge.SCHEDULED),
        DriverProfile("u-2", "이현장", "기술지원팀", ProfileBadge.RECENT),
        DriverProfile("u-3", "박관리", "총무팀"),
    )

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h640dp") // 기본 320×470dp에선 타일이 화면 밖으로 밀려 클릭이 안 닿는다
class ProfilePickerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `조작 예산 — 운전자 선택은 1탭`() {
        val actions = mutableListOf<ProfilePickerAction>()
        compose.setContent {
            AutoLogTheme {
                ProfilePickerScreen(ProfilePickerUiState(profiles, loaded = true), onAction = { actions += it })
            }
        }

        compose.onNodeWithText("이현장").performClick()

        assertEquals(listOf(ProfilePickerAction.Select("u-2")), actions)
    }

    @Test
    fun `새 운전자 추가 타일은 1탭으로 시트를 연다`() {
        val actions = mutableListOf<ProfilePickerAction>()
        compose.setContent {
            AutoLogTheme {
                ProfilePickerScreen(ProfilePickerUiState(profiles, loaded = true), onAction = { actions += it })
            }
        }

        compose.onNodeWithText("새 운전자 추가").performClick()

        assertEquals(listOf<ProfilePickerAction>(ProfilePickerAction.OpenAdd), actions)
    }

    @Test
    fun `전체 직원 팝업은 제목 없이 오른쪽 위 X 1탭으로 닫는다`() {
        val actions = mutableListOf<ProfilePickerAction>()
        compose.setContent {
            AutoLogTheme {
                ProfilePickerScreen(ProfilePickerUiState(profiles, showAll = true, loaded = true), onAction = {
                    actions +=
                        it
                })
            }
        }

        compose.onNodeWithText("운전자 선택").assertDoesNotExist()
        compose.onNodeWithContentDescription("닫기").performClick()

        assertEquals(listOf<ProfilePickerAction>(ProfilePickerAction.HideAll), actions)
    }

    @Test
    fun `추가 팝업 — 중복 안내를 보여 주고 주 버튼, 오른쪽 위 X로 끝낸다`() {
        val actions = mutableListOf<ProfilePickerAction>()
        val form = AddDriverForm(name = "김영업", dept = "영업1팀", error = AddDriverResult.Duplicate)
        compose.setContent {
            AutoLogTheme {
                ProfilePickerScreen(ProfilePickerUiState(profiles, addForm = form, loaded = true), onAction = {
                    actions +=
                        it
                })
            }
        }

        compose.onNodeWithText("이미 등록된 사용자에요").assertIsDisplayed()
        compose.onNodeWithText("추가 및 시작하기").performClick()
        compose.onNodeWithContentDescription("닫기").performClick()

        assertEquals(listOf(ProfilePickerAction.SubmitAdd, ProfilePickerAction.CancelAdd), actions)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProfilePickerScreenshotTest {
    @Test
    fun picker() = captureAll("picker") {
        ProfilePickerScreen(ProfilePickerUiState(profiles, loaded = true), onAction = {})
    }

    @Test
    fun pickerMany() = captureAll("picker_many") {
        val many = profiles + listOf("정다은", "최민수", "한지민").mapIndexed { i, n -> DriverProfile("u-${i + 4}", n, "영업2팀") }
        ProfilePickerScreen(ProfilePickerUiState(many, loaded = true), onAction = {})
    }

    @Test
    fun allProfiles() = captureAll("picker_all") {
        val many = profiles + listOf("정다은", "최민수", "한지민").mapIndexed { i, n -> DriverProfile("u-${i + 4}", n, "영업2팀") }
        ProfilePickerScreen(ProfilePickerUiState(many, showAll = true, loaded = true), onAction = {})
    }

    @Test
    fun addSheet() = captureAll("picker_add") {
        ProfilePickerScreen(
            ProfilePickerUiState(profiles, addForm = AddDriverForm(name = "최신입"), loaded = true),
            onAction = {},
        )
    }

    @Test
    fun addSheetError() = captureAll("picker_add_error") {
        ProfilePickerScreen(
            ProfilePickerUiState(
                profiles,
                addForm = AddDriverForm(name = "김영업", dept = "영업1팀", error = AddDriverResult.Duplicate),
                loaded = true,
            ),
            onAction = {},
        )
    }
}
