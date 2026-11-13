package kr.autolog.vehicle.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kr.autolog.vehicle.R
import kr.autolog.vehicle.data.repository.ConnectCode
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.CodeField
import kr.autolog.vehicle.ui.design.components.DangerButton
import kr.autolog.vehicle.ui.design.components.InputField
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.PrimaryButton

/**
 * 설정 팝업(모두 `ModalCard` — 오른쪽 위 X로 닫는다, D-30). 결정 버튼은 하단 고정(`footer`).
 * 실패하면 팝업 안에 이유 한 줄, 성공하면 ViewModel이 팝업을 닫고 섹션 아래에 결과 한 줄.
 */
@Composable
internal fun SettingsDialogHost(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val dismiss = { onAction(SettingsAction.DismissDialog) }
    val submit = { onAction(SettingsAction.SubmitDialog) }
    ModalHost(state.dialog) { d -> SettingsDialogCard(d, state, onAction, dismiss, submit) }
}

@Composable
private fun SettingsDialogCard(
    d: SettingsDialog,
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    dismiss: () -> Unit,
    submit: () -> Unit,
) {
    when (d) {
        is SettingsDialog.Register -> ModalCard(
            title = stringResource(R.string.settings_register_title),
            onClose = dismiss,
            footer = {
                PrimaryButton(
                    stringResource(R.string.settings_register_submit),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ConnectCode.isComplete(d.code),
                    loading = state.running == SettingsTask.REGISTER,
                )
            },
        ) {
            DialogBody {
                // 서버 주소는 묻지 않는다 — 서버는 하나라 빌드에 고정(2026-11-13 사용자 결정)
                // 6칸으로 보여 주고 키보드로 받는다. 서버 코드는 글자와 숫자가 섞인다(2~9, A~Z 중 I·O 제외) — 소문자로 쳐도
                // 대문자로 들어가고 0·1·I·O·기호는 들어가지 않는다(ViewModel → ConnectCode.normalize, G-01)
                CodeField(
                    stringResource(R.string.settings_register_code),
                    d.code,
                    ConnectCode.LENGTH,
                    { onAction(SettingsAction.EditDialog(DialogField.CONNECT_CODE, it)) },
                )
                DialogError(state, SettingsTask.REGISTER)
            }
        }
        is SettingsDialog.AddRecipient -> ModalCard(
            title = stringResource(R.string.settings_add_recipient),
            onClose = dismiss,
            footer = {
                PrimaryButton(
                    stringResource(R.string.action_add),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = d.email.isNotBlank(),
                    loading = state.running == SettingsTask.ADD_RECIPIENT,
                )
            },
        ) {
            DialogBody {
                InputField(
                    stringResource(R.string.settings_email),
                    d.email,
                    { onAction(SettingsAction.EditDialog(DialogField.EMAIL, it)) },
                    keyboardType = KeyboardType.Email,
                    placeholder = stringResource(R.string.settings_email_hint),
                )
                InputField(
                    stringResource(R.string.settings_name_optional),
                    d.name,
                    { onAction(SettingsAction.EditDialog(DialogField.NAME, it)) },
                    placeholder = stringResource(R.string.settings_recipient_name_hint),
                )
                DialogError(state, SettingsTask.ADD_RECIPIENT)
            }
        }
        is SettingsDialog.AddStaff -> ModalCard(
            title = stringResource(R.string.settings_add_staff),
            onClose = dismiss,
            footer = {
                PrimaryButton(
                    stringResource(R.string.action_add),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = d.name.isNotBlank(),
                    loading = state.running == SettingsTask.ADD_STAFF,
                )
            },
        ) {
            DialogBody {
                InputField(
                    stringResource(R.string.settings_name),
                    d.name,
                    { onAction(SettingsAction.EditDialog(DialogField.NAME, it)) },
                    placeholder = stringResource(R.string.picker_add_name_hint),
                )
                InputField(
                    stringResource(R.string.settings_dept_optional),
                    d.dept,
                    { onAction(SettingsAction.EditDialog(DialogField.DEPT, it)) },
                    placeholder = stringResource(R.string.picker_add_dept_hint),
                )
                InputField(
                    stringResource(R.string.settings_email_optional),
                    d.email,
                    { onAction(SettingsAction.EditDialog(DialogField.EMAIL, it)) },
                    keyboardType = KeyboardType.Email,
                    placeholder = stringResource(R.string.settings_email_hint),
                )
                DialogError(state, SettingsTask.ADD_STAFF)
            }
        }
        is SettingsDialog.RemoveRecipient -> ModalCard(
            title = stringResource(R.string.settings_remove_recipient_title),
            onClose = dismiss,
            footer = {
                DangerButton(
                    stringResource(R.string.action_remove),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            DialogBody {
                ModalText(
                    stringResource(
                        R.string.settings_remove_recipient_body,
                        d.name?.let { stringResource(R.string.settings_name_with_email, it, d.email) } ?: d.email,
                    ),
                )
                DialogError(state, SettingsTask.REMOVE_RECIPIENT)
            }
        }
    }
}

/** 팝업 내용 묶음 — 칸·글자 사이 16 dp(`ModalCard` 내용 영역은 간격을 주지 않는다). */
@Composable
private fun DialogBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s4), content = content)
}

/** 팝업 안 실패 한 줄(성공이면 팝업이 닫히므로 실패만). 버튼 바로 위라 가운데(`ModalText`). */
@Composable
private fun DialogError(state: SettingsUiState, task: SettingsTask) {
    val failed = state.results[task] as? TaskResult.Failed ?: return
    ModalText(failed.reason, color = AutoLogTheme.colors.danger)
}
