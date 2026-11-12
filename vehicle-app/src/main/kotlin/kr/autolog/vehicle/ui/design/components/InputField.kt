package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.LocalKeyboardLocked
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

/**
 * 디자인 언어 11장 InputField(채움형). 텍스트 입력은 최후의 수단(프리셋 → 최근값 → 음성 → 키보드).
 * 잠금이거나 [enabled]가 false면 비활성 + "지금은 입력할 수 없어요". [secret]이면 항상 가린다(앱 비밀번호).
 */
@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    // 주행·정차 잠금 또는 플랫폼 키보드 규제면 입력 불가 + 안내 한 줄
    val restricted = LocalInputLocked.current || LocalKeyboardLocked.current
    val locked = restricted || !enabled
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.md)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(label, style = t.caption, color = c.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = !locked,
            singleLine = true,
            textStyle = t.body.copy(color = if (locked) c.textTertiary else c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                // 채움형: 테두리 상자 대신 한 단계 위 면(surface3). 초점이면 accent 링 — 테두리만 있는 상자는 옛 안드로이드처럼 보였다
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.touchMin)
                        .clip(shape)
                        .background(if (locked) c.surface2 else c.surface3)
                        .then(if (focused) Modifier.border(Size.ring, c.accent, shape) else Modifier)
                        .padding(horizontal = Space.s5),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() &&
                        placeholder != null
                    ) {
                        Text(placeholder, style = t.body, color = c.textTertiary)
                    }
                    inner()
                }
            },
        )
        if (restricted) Text(stringResource(R.string.input_locked), style = t.caption, color = c.warning)
    }
}
