package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.LocalKeyboardLocked
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

/**
 * 디자인 언어 11장 CodeField — 정해진 길이의 코드(차량 연결 코드)를 **칸 [length]개**로 보여 주고 시스템 키보드로 받는다.
 * 칸(76 dp, `radius.sm`, `surface3`)을 누르면 키보드가 뜨고, 다음 글자가 들어갈 칸에 `accent` 초점 링.
 * 글자는 늘 끝에 붙고 끝에서 지운다(커서를 끝에 고정 — 보이지 않는 칸 중간에 끼어들지 않게).
 * 대문자 키보드·자동 수정 끔을 요청하지만 키보드마다 다르므로, 대문자화·허용 글자는 [onValueChange] 쪽 값 규칙이 맡는다.
 * 잠금이면 `InputField`처럼 비활성 + "지금은 입력할 수 없어요".
 */
@Composable
fun CodeField(
    label: String,
    value: String,
    length: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val restricted = LocalInputLocked.current || LocalKeyboardLocked.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.sm)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(label, style = t.caption, color = c.textSecondary)
        BasicTextField(
            value = TextFieldValue(value, selection = TextRange(value.length)),
            onValueChange = { onValueChange(it.text) },
            enabled = !restricted,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // 실제 입력 칸은 보이지 않게 깔아 둔다(키보드 연결·커서는 그대로) — 보이는 것은 아래 칸들
                    Box(Modifier.matchParentSize().alpha(0f)) { inner() }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        val next = value.length.coerceAtMost(length - 1)
                        repeat(length) { i ->
                            Box(
                                Modifier
                                    .size(Size.touchMin)
                                    .clip(shape)
                                    .background(if (restricted) c.surface2 else c.surface3)
                                    .then(
                                        if (focused &&
                                            i == next
                                        ) {
                                            Modifier.border(Size.ring, c.accent, shape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    value.getOrNull(i)?.toString().orEmpty(),
                                    style = t.title,
                                    color = if (restricted) c.textTertiary else c.textPrimary,
                                )
                            }
                        }
                    }
                }
            },
        )
        if (restricted) Text(stringResource(R.string.input_locked), style = t.caption, color = c.warning)
    }
}
