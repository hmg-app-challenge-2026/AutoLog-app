package kr.autolog.vehicle.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kr.autolog.vehicle.R
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.data.repository.ConnectCode
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ChoiceRow
import kr.autolog.vehicle.ui.design.components.CodeField
import kr.autolog.vehicle.ui.design.components.DangerButton
import kr.autolog.vehicle.ui.design.components.InputField
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalHost
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.components.TertiaryButton

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
        is SettingsDialog.AddPurpose -> ModalCard(
            title = stringResource(R.string.settings_add_purpose),
            onClose = dismiss,
            footer = {
                PrimaryButton(
                    stringResource(R.string.action_add),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = d.label.isNotBlank(),
                    loading = state.running == SettingsTask.ADD_PURPOSE,
                )
            },
        ) {
            DialogBody {
                InputField(
                    stringResource(R.string.settings_purpose_name),
                    d.label,
                    { onAction(SettingsAction.EditDialog(DialogField.NAME, it)) },
                    placeholder = stringResource(R.string.settings_purpose_name_hint),
                )
                ChoiceLabel(stringResource(R.string.settings_purpose_category))
                PURPOSE_CATEGORIES.forEach { cat ->
                    ChoiceRow(
                        stringResource(purposeCategoryText(cat)),
                        selected = d.category == cat,
                        onClick = { onAction(SettingsAction.SetPurposeCategory(cat)) },
                    )
                }
                DialogError(state, SettingsTask.ADD_PURPOSE)
            }
        }
        is SettingsDialog.RemovePurpose -> ModalCard(
            title = stringResource(R.string.settings_remove_purpose_title),
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
                ModalText(stringResource(R.string.settings_remove_purpose_body, d.label))
                DialogError(state, SettingsTask.REMOVE_PURPOSE)
            }
        }
        is SettingsDialog.AddPurposeRule -> ModalCard(
            title = stringResource(R.string.settings_add_purpose_rule),
            onClose = dismiss,
            footer = {
                PrimaryButton(
                    stringResource(R.string.action_add),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = d.complete,
                    loading = state.running == SettingsTask.ADD_PURPOSE_RULE,
                )
            },
        ) {
            DialogBody {
                if (d.places.isEmpty()) ModalText(stringResource(R.string.settings_rule_no_places))
                ChoiceLabel(stringResource(R.string.settings_rule_origin))
                ChoiceRow(
                    stringResource(R.string.settings_rule_anywhere),
                    selected = d.originPlaceId == null,
                    onClick = { onAction(SettingsAction.PickRuleOrigin(null)) },
                )
                d.places.forEach { p ->
                    ChoiceRow(p.label, d.originPlaceId == p.placeId, {
                        onAction(SettingsAction.PickRuleOrigin(p.placeId))
                    })
                }
                TertiaryButton(
                    stringResource(R.string.settings_new_place),
                    onClick = { onAction(SettingsAction.StartAddPlace(RuleTarget.ORIGIN)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceLabel(stringResource(R.string.settings_rule_dest))
                d.places.forEach { p ->
                    ChoiceRow(p.label, d.destPlaceId == p.placeId, { onAction(SettingsAction.PickRuleDest(p.placeId)) })
                }
                TertiaryButton(
                    stringResource(R.string.settings_new_place),
                    onClick = { onAction(SettingsAction.StartAddPlace(RuleTarget.DEST)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceLabel(stringResource(R.string.settings_rule_purpose))
                d.purposes.forEach { p ->
                    ChoiceRow(
                        stringResource(
                            R.string.settings_purpose_with_category,
                            p.label,
                            stringResource(purposeCategoryText(p.category)),
                        ),
                        d.purposeName == p.name,
                        { onAction(SettingsAction.PickRulePurpose(p.name)) },
                    )
                }
                DialogError(state, SettingsTask.ADD_PURPOSE_RULE)
            }
        }
        is SettingsDialog.AddPlace -> ModalCard(
            title = stringResource(R.string.settings_new_place_title),
            onClose = dismiss,
            footer = {
                PrimaryButton(
                    stringResource(R.string.action_add),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = d.complete,
                    loading = state.running == SettingsTask.ADD_PLACE,
                )
            },
        ) {
            DialogBody {
                InputField(
                    stringResource(R.string.settings_place_query),
                    d.query,
                    { onAction(SettingsAction.EditDialog(DialogField.QUERY, it)) },
                    placeholder = stringResource(R.string.settings_place_query_hint),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    SecondaryButton(
                        stringResource(R.string.settings_place_search),
                        onClick = { onAction(SettingsAction.SearchPlace) },
                        modifier = Modifier.weight(1f),
                        enabled = d.query.trim().length >= 2 && state.running == null,
                    )
                    SecondaryButton(
                        stringResource(R.string.settings_place_here),
                        onClick = { onAction(SettingsAction.UseCurrentPlace) },
                        modifier = Modifier.weight(1f),
                        enabled = state.running == null,
                    )
                }
                DialogError(state, SettingsTask.SEARCH_PLACE)
                val separator = stringResource(R.string.meta_separator)
                val here = stringResource(R.string.settings_place_here)
                d.candidates?.let { list ->
                    if (list.isEmpty()) ModalText(stringResource(R.string.settings_place_none))
                    list.forEachIndexed { i, c ->
                        ChoiceRow(
                            listOfNotNull(c.name, c.address).distinct().joinToString(separator).ifEmpty { here },
                            selected = d.picked == i,
                            onClick = { onAction(SettingsAction.PickCandidate(i)) },
                        )
                    }
                }
                if (d.picked != null) {
                    InputField(
                        stringResource(R.string.settings_place_name),
                        d.label,
                        { onAction(SettingsAction.EditDialog(DialogField.NAME, it)) },
                        placeholder = stringResource(R.string.settings_place_name_hint),
                    )
                }
                DialogError(state, SettingsTask.ADD_PLACE)
            }
        }
        is SettingsDialog.RemovePurposeRule -> ModalCard(
            title = stringResource(R.string.settings_remove_purpose_rule_title),
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
                        R.string.settings_remove_purpose_rule_body,
                        ruleRouteText(d.rule),
                        d.rule.purposeLabel,
                    ),
                )
                DialogError(state, SettingsTask.REMOVE_PURPOSE_RULE)
            }
        }
    }
}

/** 고르기 묶음 위 작은 제목(출발·도착·목적·구분). */
@Composable
private fun ChoiceLabel(text: String) {
    Text(text, style = AutoLogTheme.type.section, color = AutoLogTheme.colors.textSecondary)
}

/** 목적 추가에서 고르는 구분 — 미확정은 고르지 않는다. */
private val PURPOSE_CATEGORIES = listOf(PurposeCategory.BUSINESS, PurposeCategory.COMMUTE, PurposeCategory.NON_BUSINESS)

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
