package kr.autolog.vehicle.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.NumericKeypad
import kr.autolog.vehicle.ui.design.components.SecondaryButton

/**
 * 운행 거리 고치기 팝업(F-05) — 주유 입력의 숫자 칸 팝업(`FieldEditor`)과 같은 모양: 제목 + 값(`headline` + km) + 숫자판.
 * 값을 검사해야 해서 [완료]로 끝내고(범위 밖이면 이유를 값 아래 빨갛게), 오른쪽 위 X는 고치지 않고 닫는다.
 * 계기판 원본은 앱 A가 따로 남기므로 여기서 고친 값은 "수정됨"으로 보인다.
 */
@Composable
internal fun DistanceEditDialog(editor: DistanceEditor, onAction: (TripResultAction) -> Unit) {
    val c = AutoLogTheme.colors
    ModalCard(
        title = stringResource(R.string.result_distance_title),
        onClose = { onAction(TripResultAction.CancelDistance) },
        footer = {
            SecondaryButton(
                stringResource(R.string.action_done),
                onClick = { onAction(TripResultAction.ConfirmDistance) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(
                    editor.draft.ifEmpty { stringResource(R.string.value_zero) },
                    modifier = Modifier.alignByBaseline(),
                    style = AutoLogTheme.type.headline,
                    color = if (editor.draft.isEmpty()) c.textTertiary else c.textPrimary,
                )
                Text(
                    stringResource(R.string.unit_km),
                    modifier = Modifier.alignByBaseline(),
                    style = AutoLogTheme.type.body,
                    color = c.textTertiary,
                )
            }
            editor.error?.let {
                ModalText(stringResource(distanceErrorText(it)), color = c.danger)
            }
            Spacer(Modifier.height(Space.s4))
            NumericKeypad(
                onDigit = { onAction(TripResultAction.DistanceKey(it)) },
                onDelete = { onAction(TripResultAction.DistanceDelete) },
                allowDecimal = true,
            )
        }
    }
}

private fun distanceErrorText(error: DistanceEntryError): Int = when (error) {
    DistanceEntryError.EMPTY_OR_ZERO -> R.string.result_distance_error_zero
    DistanceEntryError.TOO_LONG -> R.string.result_distance_error_long
}
