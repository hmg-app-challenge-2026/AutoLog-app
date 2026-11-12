package kr.autolog.vehicle.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import kr.autolog.vehicle.R
import kr.autolog.vehicle.data.repository.AddDriverResult
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.AvatarFace
import kr.autolog.vehicle.ui.design.components.IconAvatar
import kr.autolog.vehicle.ui.design.components.InputField
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.initialsOf

/**
 * 스펙 7.1 [+ 새 운전자 추가] 팝업(F-24, origin=LOCAL). 가운데 카드(`ModalCard`), 위에서 아래로 한 흐름:
 * 머리 줄(제목 "새 운전자 추가" 가운데 · 닫기 X 오른쪽) → 아바타 미리보기(이름을 치면 이니셜) → 이름·부서 → 안내 메시지 → [추가 및 시작하기].
 * 버튼·안내는 카드 하단에 고정(내용만 스크롤). 주 버튼은 전폭, 높이는 터치 하한 76 dp.
 * 아바타 TALL 160 dp, 그 외 120 dp. WIDE·NARROW는 이름·부서를 한 줄에 나란히 둔다.
 */
@Composable
fun AddDriverDialog(form: AddDriverForm, onAction: (ProfilePickerAction) -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalWindowLayout.current
    ModalCard(
        modifier,
        title = stringResource(R.string.picker_add),
        footer = { AddDriverButtons(form, onAction) },
        onClose = { onAction(ProfilePickerAction.CancelAdd) },
    ) {
        Spacer(Modifier.height(Space.s3))
        NewDriverAvatar(
            form.name,
            if (layout == WindowLayout.TALL) Size.avatarPreviewTall else Size.avatarPreview,
            Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Space.s6))
        AddDriverFields(form, onAction, sideBySide = layout != WindowLayout.TALL)
    }
}

@Composable
private fun AddDriverFields(form: AddDriverForm, onAction: (ProfilePickerAction) -> Unit, sideBySide: Boolean) {
    val name =
        @Composable { m: Modifier ->
            InputField(
                label = stringResource(R.string.picker_add_name),
                value = form.name,
                onValueChange = { onAction(ProfilePickerAction.AddNameChanged(it)) },
                enabled = !form.saving,
                placeholder = stringResource(R.string.picker_add_name_hint),
                modifier = m,
            )
        }
    val dept =
        @Composable { m: Modifier ->
            InputField(
                label = stringResource(R.string.picker_add_dept),
                value = form.dept,
                onValueChange = { onAction(ProfilePickerAction.AddDeptChanged(it)) },
                enabled = !form.saving,
                placeholder = stringResource(R.string.picker_add_dept_hint),
                modifier = m,
            )
        }
    if (sideBySide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
            name(Modifier.weight(1f))
            dept(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s5)) {
            name(Modifier.fillMaxWidth())
            dept(Modifier.fillMaxWidth())
        }
    }
}

/**
 * 안내 메시지(가운데) + 주 버튼 전폭. 취소는 오른쪽 위 X.
 * 안내 메시지를 버튼 바로 위(고정 영역)에 두는 이유: 좁은 창에서 입력 칸 아래에 두면 스크롤 밖에 숨어 누른 결과가 안 보였다.
 */
@Composable
private fun AddDriverButtons(form: AddDriverForm, onAction: (ProfilePickerAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        form.error?.let {
            ModalText(errorText(it), Modifier.padding(bottom = Space.s4), color = AutoLogTheme.colors.danger)
        }
        PrimaryButton(
            stringResource(R.string.picker_add_confirm),
            onClick = { onAction(ProfilePickerAction.SubmitAdd) },
            modifier = Modifier.fillMaxWidth(),
            height = Size.buttonMd,
            enabled = form.canSubmit,
            loading = form.saving,
        )
    }
}

/** 이름이 비면 사람+ 아이콘, 치기 시작하면 이니셜. 아직 id가 없어 사람별 색 대신 accent 그라데이션(새 사람 표시). */
@Composable
private fun NewDriverAvatar(name: String, size: Dp, modifier: Modifier) {
    val c = AutoLogTheme.colors
    if (name.isBlank()) {
        IconAvatar(
            Icons.Rounded.PersonAdd,
            size,
            modifier,
            iconSize = if (size >
                Size.avatarSmall
            ) {
                Size.iconLg
            } else {
                Size.icon
            },
        )
    } else {
        AvatarFace(initialsOf(name), Brush.linearGradient(listOf(c.accent, c.accentFill)), size, modifier)
    }
}

@Composable
private fun errorText(failure: AddDriverResult.Failure): String = when (failure) {
    AddDriverResult.EmptyName -> stringResource(R.string.picker_add_name_required)
    AddDriverResult.Duplicate -> stringResource(R.string.picker_add_duplicate)
    is AddDriverResult.Rejected -> failure.reason
}
