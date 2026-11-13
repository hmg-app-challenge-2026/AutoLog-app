package kr.autolog.vehicle.ui.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalDrivingLocked
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import kr.autolog.vehicle.ui.trips.TripDay
import kr.autolog.vehicle.ui.trips.TripWhen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 기어 P로 종료 대기 — 보기 모드, 3초 남음, [기록 이어가기] 가능. */
private val viewing =
    TripResultUiState(
        loading = false,
        km = "48.2",
        minutes = 52,
        origin = "판교 테크노밸리",
        destination = "서울 강남구 역삼동",
        autoCloseRemainingMs = 3_000,
        canContinue = true,
    )

/** 이미 끝난 운행(직접 [기록 종료]·이력에서 열기) — [기록 이어가기] 없음. */
private val ended = viewing.copy(canContinue = false)

/** [수정하기] 뒤 — 카운트다운 멈춤, 출퇴근을 고름. */
private val editing = viewing.copy(editing = true, autoCloseRemainingMs = null, selected = PurposeCategory.COMMUTE)

/** 정차·주행 중(루트가 주는 잠금, D-31·D-35). */
@Composable
private fun Driving(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalInputLocked provides true, LocalDrivingLocked provides true, content = content)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h640dp")
class TripResultScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: TripResultUiState, actions: MutableList<TripResultAction>) = compose.setContent {
        AutoLogTheme { WindowLayoutHost { TripResultScreen(state, onAction = { actions += it }) } }
    }

    @Test
    fun `보기 모드 — 분류와 자동 저장 안내, 라디오는 눌러도 아무 일 없고 기록 이어가기·수정하기·저장하기`() {
        val actions = mutableListOf<TripResultAction>()
        show(viewing, actions)

        compose.onNodeWithText("운행 기록을 완료했어요").assertIsDisplayed()
        compose.onNodeWithText("일반 업무용 거리 48.2 km로 분류돼요.").assertIsDisplayed()
        compose.onNodeWithText("3초 후 자동 저장돼요.").assertIsDisplayed() // 남은 시간은 분류 문구 뒤 한 문장(버튼 글자엔 없다)
        compose.onNodeWithText("출퇴근").performClick()
        compose.onNodeWithText("기록 이어가기").performClick()
        compose.onNodeWithText("수정하기").performClick()
        compose.onNodeWithText("저장하기").performClick()

        assertEquals(listOf(TripResultAction.Continue, TripResultAction.Edit, TripResultAction.Save), actions)
    }

    @Test
    fun `저장 완료 — 문구와 확인, 체크를 다 그리면 알리고 확인은 닫기`() {
        val actions = mutableListOf<TripResultAction>()
        show(ended.copy(saved = true), actions)

        compose.onNodeWithText("운행 기록을 저장했어요").assertIsDisplayed()
        compose.onNodeWithText("저장하기").assertDoesNotExist()
        compose.onNodeWithText("확인").performClick()

        assertEquals(listOf(TripResultAction.CheckDrawn, TripResultAction.Close), actions)
    }

    @Test
    fun `이미 끝난 운행엔 기록 이어가기가 없다`() {
        show(ended, mutableListOf())

        compose.onNodeWithText("기록 이어가기").assertDoesNotExist()
    }

    @Test
    fun `정차·주행 중 — 아래는 남은 시간과 안내 두 줄, 버튼·분류 문구 없음`() {
        compose.setContent {
            AutoLogTheme { WindowLayoutHost { Driving { TripResultScreen(ended, onAction = {}) } } }
        }

        compose.onNodeWithText("주차하면 운행 기록을 수정할 수 있어요").assertIsDisplayed()
        compose.onNodeWithText("3초 후 자동 저장돼요.").assertIsDisplayed()
        compose.onNodeWithText("일반 업무용 거리 48.2 km로 분류돼요.").assertDoesNotExist()
        compose.onNodeWithText("수정하기").assertDoesNotExist()
        compose.onNodeWithText("저장하기").assertDoesNotExist()
        compose.onNodeWithText("기록 이어가기").assertDoesNotExist()
    }

    @Test
    fun `정차·주행 중 저장 완료 — 확인 없이 체크와 문구만`() {
        compose.setContent {
            AutoLogTheme { WindowLayoutHost { Driving { TripResultScreen(ended.copy(saved = true), onAction = {}) } } }
        }

        compose.onNodeWithText("운행 기록을 저장했어요").assertIsDisplayed()
        compose.onNodeWithText("확인").assertDoesNotExist()
    }

    @Test
    fun `수정 모드엔 빨간 삭제하기, 누르면 확인 팝업 — 확인·취소`() {
        val actions = mutableListOf<TripResultAction>()
        show(editing, actions)
        compose.onNodeWithText("삭제하기").performClick()
        assertEquals(listOf<TripResultAction>(TripResultAction.Delete), actions)
    }

    @Test
    fun `사후 확인 — 왼쪽 위 뒤로, 아래는 수정하기·삭제하기(닫기 없음)`() {
        val actions = mutableListOf<TripResultAction>()
        show(
            ended.copy(
                review = true,
                startedAt = TripWhen(TripDay.EARLIER, 11, 7, "19:34"),
                autoCloseRemainingMs = null,
            ),
            actions,
        )

        compose.onNodeWithText("닫기").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("수정하기").performClick()
        compose.onNodeWithText("삭제하기").performClick()

        assertEquals(listOf(TripResultAction.Close, TripResultAction.Edit, TripResultAction.Delete), actions)
    }

    @Test
    fun `삭제 확인 팝업 — 이 운행을 삭제할까요, 확인이면 삭제·취소면 닫기`() {
        val actions = mutableListOf<TripResultAction>()
        show(editing.copy(confirmingDelete = true), actions)

        compose.onNodeWithText("이 운행을 삭제할까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("확인").performClick()

        assertEquals(listOf(TripResultAction.CancelDelete, TripResultAction.ConfirmDelete), actions)
    }

    @Test
    fun `수정 모드 — 수정하기가 빠지고 빨간 삭제하기가 생긴다, 라디오로 고르고 저장하기`() {
        val actions = mutableListOf<TripResultAction>()
        show(editing, actions)

        compose.onNodeWithText("수정하기").assertDoesNotExist()
        compose.onNodeWithText("3초 후 자동 저장돼요.").assertDoesNotExist() // 수정 중엔 시간이 멈춘다
        compose.onNodeWithText("개인 용무").performClick()
        compose.onNodeWithText("저장하기").performClick()

        assertEquals(listOf(TripResultAction.Select(PurposeCategory.NON_BUSINESS), TripResultAction.Save), actions)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TripResultScreenshotTest {
    /** 저장 완료 — 체크 · "운행 기록을 저장했어요" · [확인](스크린샷은 정지형이라 체크가 다 그려진 모양). */
    @Test
    fun saved() = captureAll("trip_result_saved") { TripResultScreen(ended.copy(saved = true), onAction = {}) }

    // 실제 앱처럼 운전자 배지 없이(FullApp.showsDriverBadge)
    @Test
    fun viewing() = captureAll("trip_result") { TripResultScreen(viewing, onAction = {}) }

    @Test
    fun editing() = captureAll("trip_result_editing") {
        TripResultScreen(editing, onAction = {})
    }

    @Test
    fun ended() = captureAll("trip_result_ended") { TripResultScreen(ended, onAction = {}) }

    /** 수정 모드 [삭제하기] → 확인 팝업(D-39): "이 운행을 삭제할까요?" + [확인](빨강) · [취소]. */
    @Test
    fun deleteConfirm() = captureAll("trip_result_delete_confirm") {
        TripResultScreen(editing.copy(confirmingDelete = true), onAction = {})
    }

    /** 정차·주행 중 [기록 종료]·자동 종료로 끝난 운행(D-35): 아래는 남은 시간 + 안내 두 줄 — 막대·분류 문구·버튼 없음. */
    @Test
    fun driving() = captureAll("trip_result_driving") { Driving { TripResultScreen(ended, onAction = {}) } }

    /** 정차·주행 중 저장 완료(D-35): [확인] 없이 체크 · 문구(같은 자리). */
    @Test
    fun savedDriving() = captureAll("trip_result_saved_driving") {
        Driving { TripResultScreen(ended.copy(saved = true), onAction = {}) }
    }

    /** 운행 기록 조회에서 연 사후 확인(D-29·D-39): 왼쪽 위 <, 제목은 운행 시각, 카운트다운 없음, [수정하기] · 빨간 [삭제하기]. */
    @Test
    fun review() = captureAll("trip_result_review") {
        TripResultScreen(
            ended.copy(
                review = true,
                startedAt = TripWhen(TripDay.EARLIER, 11, 7, "19:34"),
                autoCloseRemainingMs = null,
            ),
            onAction = {},
        )
    }

    /** 사후 확인 수정 모드(D-39): [저장하기] · 빨간 [삭제하기] — 삭제는 보기 때와 같은 오른쪽 자리. */
    @Test
    fun reviewEditing() = captureAll("trip_result_review_editing") {
        TripResultScreen(
            editing.copy(
                review = true,
                canContinue = false,
                startedAt = TripWhen(TripDay.EARLIER, 11, 7, "19:34"),
            ),
            onAction = {},
        )
    }
}
