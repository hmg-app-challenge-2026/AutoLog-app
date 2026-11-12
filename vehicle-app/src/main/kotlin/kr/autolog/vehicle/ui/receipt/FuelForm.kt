package kr.autolog.vehicle.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import kr.autolog.shared.OcrStatus
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ChoiceRow
import kr.autolog.vehicle.ui.design.components.ConfidenceRow
import kr.autolog.vehicle.ui.design.components.InputField
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.NumericKeypad
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.components.tappable

/**
 * 입력 칸 목록 → 검산 결과 → 연결할 운행 → 오류. 영수증이면 모든 칸이 신뢰도 표시(`ConfidenceRow`),
 * 직접 입력이면 공통 칸(금액·리터·단가)만 단순한 행으로.
 */
@Composable
internal fun FuelForm(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        if (state.loading) {
            Text(stringResource(R.string.fuel_loading), style = t.body, color = c.textSecondary)
            return@Column
        }
        val receipt = state.receipt
        if (receipt?.ocrStatus == OcrStatus.FAILED) {
            Text(stringResource(R.string.fuel_ocr_failed), style = t.caption, color = c.warning)
        }
        state.fields.forEach { field ->
            val onEdit = { onAction(FuelEntryAction.Edit(field)) }
            val label = stringResource(field.label)
            val value = fieldText(field, state[field])
            if (receipt != null) {
                ConfidenceRow(label, value, state.confidence(field), onClick = onEdit)
            } else {
                PlainFieldRow(label, value, onEdit)
            }
        }
        state.arithOk?.let { ok ->
            Text(
                stringResource(if (ok) R.string.fuel_arith_ok else R.string.fuel_arith_failed),
                style = t.caption,
                color = if (ok) c.success else c.danger,
            )
        }
        TripChoices(state, onAction)
        state.error?.let { Text(errorText(it), style = t.caption, color = c.danger) }
    }
}

/** "연결할 운행" + 후보들 + [연결 안 함]. */
@Composable
private fun TripChoices(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit) {
    Text(
        stringResource(R.string.fuel_link_trip),
        style = AutoLogTheme.type.section,
        color = AutoLogTheme.colors.textSecondary,
    )
    state.trips.forEach { trip ->
        ChoiceRow(
            tripText(trip),
            selected = state.selectedTripId == trip.tripId,
            onClick = { onAction(FuelEntryAction.SelectTrip(trip.tripId)) },
        )
    }
    ChoiceRow(
        stringResource(R.string.fuel_no_trip),
        selected = state.selectedTripId == null,
        onClick = { onAction(FuelEntryAction.SelectTrip(null)) },
    )
}

/** 직접 입력의 칸 한 줄: 왼쪽 이름, 오른쪽 값(없으면 "—"). 누르면 숫자판. */
@Composable
private fun PlainFieldRow(label: String, value: String?, onClick: () -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .tappable(onClick = onClick)
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surface1)
            .padding(Space.s5),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = t.caption, color = c.textSecondary)
        Text(value ?: stringResource(R.string.value_empty), style = t.body, color = c.textPrimary)
    }
}

/** 지금 고치는 칸의 팝업: 숫자 칸은 숫자판, 글자 칸은 입력 칸 + [완료]. */
@Composable
internal fun FieldEditor(field: FuelField, state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit) {
    val title = stringResource(field.label)
    val close = { onAction(FuelEntryAction.CloseEditor) }
    if (field.input.numeric) {
        ModalCard(title = title, onClose = close) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state[field].ifBlank { stringResource(R.string.value_zero) },
                    style = AutoLogTheme.type.headline,
                    color = AutoLogTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(Space.s4))
                NumericKeypad(
                    onDigit = { onAction(FuelEntryAction.Key(it)) },
                    onDelete = { onAction(FuelEntryAction.Delete) },
                    allowDecimal = field.input == FuelInput.DECIMAL,
                )
            }
        }
    } else {
        ModalCard(
            title = title,
            onClose = close,
            footer = {
                SecondaryButton(
                    stringResource(R.string.action_done),
                    onClick = close,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            InputField(title, state[field], { onAction(FuelEntryAction.TextChanged(it)) })
        }
    }
}

/** 칸 값 표시: 숫자는 쉼표·단위, 카드는 "•••• 1234", 비었으면 null. */
@Composable
private fun fieldText(field: FuelField, raw: String): String? {
    val value = formatFuelValue(field, raw) ?: return null
    val unit = field.unit
    return when {
        unit != null -> stringResource(R.string.fuel_value_unit, value, stringResource(unit))
        field.input == FuelInput.LAST4 -> stringResource(R.string.fuel_card_masked, value)
        else -> value
    }
}

@Composable
private fun tripText(trip: FuelTripOption): String {
    val name =
        when (val label = trip.label) {
            is FuelTripLabel.Named -> label.text
            FuelTripLabel.Current -> stringResource(R.string.fuel_trip_current)
            FuelTripLabel.JustEnded -> stringResource(R.string.fuel_trip_just_ended)
        }
    return if (trip.hasFuelStop) name + stringResource(R.string.fuel_stop_badge) else name
}

@Composable
private fun errorText(error: FuelEntryError): String = when (error) {
    FuelEntryError.ReceiptMissing -> stringResource(R.string.fuel_receipt_missing)
    is FuelEntryError.Rejected -> error.reason
}
