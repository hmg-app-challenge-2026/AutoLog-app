package kr.autolog.vehicle.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.DangerButton
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.SecondaryButton

/**
 * 운행 삭제 확인(D-39) — 되돌릴 수 없어 한 번 묻는다(디자인 언어 원칙 9). "이 운행을 삭제할까요?" + [확인](빨강) · [취소]
 * 순(2026-11-13 사용자 결정). X 없이 두 버튼으로만 끝낸다(결정이 필요한 팝업 — 디자인 언어 11장).
 */
@Composable
internal fun DeleteTripDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    ModalCard(
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                DangerButton(
                    stringResource(R.string.action_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        ModalText(
            stringResource(R.string.result_delete_question),
            style = AutoLogTheme.type.title,
            color = AutoLogTheme.colors.textPrimary,
        )
    }
}
