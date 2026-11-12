package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.data.repository.RecoveryAction
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.DangerButton
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.ModalTitle
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.components.TertiaryButton

/** Backend F-10 복구. 되돌릴 수 없는 [삭제]만 한 번 더 묻는다(디자인 언어 2.6). */
@Composable
fun RecoveryDialog(extraKm: String?, onAction: (RecoveryAction) -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // 처음 단계는 반드시 하나를 골라야 해서 닫기가 없다. 삭제 확인 단계의 X는 앞 단계로 돌아간다
    ModalCard(onClose = if (confirmingDelete) ({ confirmingDelete = false }) else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            if (!confirmingDelete) {
                ModalTitle(stringResource(R.string.recovery_title))
                extraKm?.let {
                    ModalText(stringResource(R.string.recovery_body, it), style = t.title)
                }
                PrimaryButton(
                    text = stringResource(R.string.recovery_record),
                    onClick = { onAction(RecoveryAction.RECORD) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.recovery_continue),
                    onClick = { onAction(RecoveryAction.CONTINUE) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TertiaryButton(
                    text = stringResource(R.string.recovery_delete),
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ModalText(stringResource(R.string.recovery_delete_confirm), style = t.title, color = c.textPrimary)
                DangerButton(
                    text = stringResource(R.string.recovery_delete_yes),
                    onClick = { onAction(RecoveryAction.DELETE) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
