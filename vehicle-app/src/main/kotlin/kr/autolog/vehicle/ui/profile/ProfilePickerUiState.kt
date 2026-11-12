package kr.autolog.vehicle.ui.profile

import kr.autolog.vehicle.data.repository.AddDriverResult
import kr.autolog.vehicle.data.repository.DriverProfile

/** 스펙 7.1: 사람 타일 최대 5개, 넘치면 그 뒤에 [다른 사람]. [+ 새 운전자 추가]는 항상 맨 끝. */
const val PICKER_VISIBLE_MAX = 5

/** 선택 화면 한 줄에 놓이는 타일. 화면은 이 목록을 순서대로 그리기만 한다(배치 규칙은 여기 한 곳). */
sealed interface PickerTile {
    data class Person(val profile: DriverProfile) : PickerTile

    data object More : PickerTile

    data object Add : PickerTile
}

/** 새 운전자 추가 시트의 입력 상태. null이면 시트가 닫혀 있다. */
data class AddDriverForm(
    val name: String = "",
    val dept: String = "",
    val error: AddDriverResult.Failure? = null,
    val saving: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && !saving
}

data class ProfilePickerUiState(
    val profiles: List<DriverProfile> = emptyList(),
    val showAll: Boolean = false,
    val addForm: AddDriverForm? = null,
    val loaded: Boolean = false,
) {
    val hasMore: Boolean get() = profiles.size > PICKER_VISIBLE_MAX

    val tiles: List<PickerTile>
        get() =
            buildList {
                profiles.take(PICKER_VISIBLE_MAX).mapTo(this) { PickerTile.Person(it) }
                if (hasMore) add(PickerTile.More)
                add(PickerTile.Add)
            }
}

sealed interface ProfilePickerAction {
    data class Select(val userId: String) : ProfilePickerAction

    data object Later : ProfilePickerAction

    data object ShowAll : ProfilePickerAction

    data object HideAll : ProfilePickerAction

    data object OpenAdd : ProfilePickerAction

    data class AddNameChanged(val value: String) : ProfilePickerAction

    data class AddDeptChanged(val value: String) : ProfilePickerAction

    /** 추가하고 바로 그 사람으로 시작(한 번에 끝 — 조작 예산 1.2). */
    data object SubmitAdd : ProfilePickerAction

    data object CancelAdd : ProfilePickerAction
}
