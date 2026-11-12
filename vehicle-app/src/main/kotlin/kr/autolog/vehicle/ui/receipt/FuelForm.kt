package kr.autolog.vehicle.ui.receipt

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import coil.compose.SubcomposeAsyncImage
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ChoiceRow
import kr.autolog.vehicle.ui.design.components.ConfidenceRow
import kr.autolog.vehicle.ui.design.components.InputField
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.NumericKeypad
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.components.Section
import kr.autolog.vehicle.ui.design.components.tappable
import kr.autolog.vehicle.ui.design.twoColumns

/** 요약 아래 영수증 사진 썸네일 크기 — 대조용으로 알아볼 만큼만, 누르면 크게(D-38: 예전엔 넓은 화면 절반을 차지했다). */
private val THUMB_WIDTH = Space.s9 * 2
private val THUMB_HEIGHT = Space.s9 * 2 + Space.s7

/** 요약 보조 첫 줄(유종 · 주유량 · 리터당 가격 · 할인)과 둘째 줄(주유소 · 일시)에 놓이는 칸. */
private val SUMMARY_LINE_1 = listOf(FuelField.FUEL_TYPE, FuelField.LITERS, FuelField.UNIT_PRICE)
private val SUMMARY_LINE_2 = listOf(FuelField.MERCHANT, FuelField.DATETIME)

/**
 * 왼쪽 "주유 내역"(D-38): 영수증 요약 또는 칸 목록, 영수증이면 그 아래 작은 사진.
 * 두 칸 배치는 소제목 아래를 16 dp 더 띄운다 — 운행 결과와 같은 규칙.
 */
@Composable
internal fun FuelDetails(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit, onImage: () -> Unit) {
    FuelSection(R.string.fuel_section_details) {
        if (state.showsSummary) FuelSummary(state) else FuelFieldList(state, onAction)
        if (state.receipt != null) {
            Spacer(Modifier.height(Space.s6))
            ReceiptThumbnail(state, onImage)
        }
    }
}

/** 오른쪽 "연결할 운행": 후보들 + [연결 안 함]. 운행 결과의 운행 목적과 같은 선택형 행. */
@Composable
internal fun FuelTripChoices(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit) {
    FuelSection(R.string.fuel_link_trip) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
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
    }
}

@Composable
private fun FuelSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    val twoColumns = LocalWindowLayout.current.twoColumns
    Section(stringResource(title)) {
        if (twoColumns) Spacer(Modifier.height(Space.s4))
        content()
    }
}

/**
 * 영수증 요약: 결제 금액이 주인공(운행 결과의 거리와 같은 크기), 그 아래 보조 두 줄.
 * 봐야 할 값만 주황으로 — 믿을 만한 값엔 아무 표시도 하지 않는다(정상은 조용히). 고치는 곳은 [수정하기]의 칸 목록.
 */
@Composable
private fun FuelSummary(state: FuelEntryUiState) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val check = state.needsCheck.toSet()
    Text(
        fieldText(FuelField.AMOUNT, state[FuelField.AMOUNT]).orEmpty(),
        style = t.resultDistance,
        color = if (FuelField.AMOUNT in check) c.warning else c.textPrimary,
    )
    Spacer(Modifier.height(Space.s3))
    SummaryLine(state, SUMMARY_LINE_1 + discountIfAny(state), check, t.title)
    SummaryLine(state, SUMMARY_LINE_2, check, t.body)
}

/** 할인은 0이 아닐 때만 요약에 — 대부분 0이라 늘 보이면 소음이다. */
private fun discountIfAny(state: FuelEntryUiState): List<FuelField> =
    if ((state[FuelField.DISCOUNT].toIntOrNull() ?: 0) > 0) listOf(FuelField.DISCOUNT) else emptyList()

@Composable
private fun SummaryLine(state: FuelEntryUiState, fields: List<FuelField>, check: Set<FuelField>, style: TextStyle) {
    val c = AutoLogTheme.colors
    val separator = stringResource(R.string.meta_separator)
    val parts = fields.mapNotNull { f -> fieldText(f, state[f])?.let { f to it } }
    if (parts.isEmpty()) return
    val text =
        buildAnnotatedString {
            parts.forEachIndexed { i, (field, value) ->
                if (i > 0) append(separator)
                val shown = if (field ==
                    FuelField.DISCOUNT
                ) {
                    stringResource(R.string.fuel_discount_short, value)
                } else {
                    value
                }
                if (field in check) withStyle(SpanStyle(color = c.warning)) { append(shown) } else append(shown)
            }
        }
    Text(text, style = style, color = c.textSecondary)
}

/** 칸 목록: 봐야 할 칸이 먼저(`listedFields`), 결제 금액은 한 단계 크게. 빈 칸은 "눌러서 입력". 누르면 숫자판·입력 칸 팝업. */
@Composable
private fun FuelFieldList(state: FuelEntryUiState, onAction: (FuelEntryAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        state.listedFields.forEach { field ->
            ConfidenceRow(
                stringResource(field.label),
                fieldText(field, state[field]),
                state.confidence(field),
                onClick = { onAction(FuelEntryAction.Edit(field)) },
                placeholder = stringResource(R.string.fuel_input_hint),
                emphasized = field == FuelField.AMOUNT,
            )
        }
    }
}

/** 작은 사진 + "눌러서 크게 보기". */
@Composable
private fun ReceiptThumbnail(state: FuelEntryUiState, onClick: () -> Unit) {
    Row(
        Modifier.tappable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s5),
    ) {
        ReceiptImage(state, Modifier.size(THUMB_WIDTH, THUMB_HEIGHT), onClick = null)
        Text(
            stringResource(R.string.fuel_receipt_zoom),
            style = AutoLogTheme.type.caption,
            color = AutoLogTheme.colors.textSecondary,
        )
    }
}

/** 영수증 사진. 불러오는 중·실패는 사진 자리에 문구. [onClick]이 있으면 사진 자체를 누를 수 있다(확대 팝업에서 닫기). */
@Composable
internal fun ReceiptImage(state: FuelEntryUiState, modifier: Modifier, onClick: (() -> Unit)?) {
    Box(
        modifier
            .tappable(onClick = onClick)
            .clip(RoundedCornerShape(Radius.lg))
            .background(AutoLogTheme.colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = state.receipt?.thumbUrl,
            contentDescription = stringResource(R.string.fuel_receipt_image),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            loading = { ImagePlaceholder(stringResource(R.string.fuel_receipt_image)) },
            error = { ImagePlaceholder(stringResource(R.string.fuel_receipt_image_unavailable)) },
        )
    }
}

@Composable
private fun ImagePlaceholder(text: String) {
    Box(Modifier.fillMaxSize().padding(Space.s3), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = AutoLogTheme.type.caption,
            color = AutoLogTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
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
