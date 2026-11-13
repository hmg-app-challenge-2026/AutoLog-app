package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

/** OCR 신뢰도 3단계(스펙 7.5). 값이 없으면 MISSING. */
enum class ConfidenceLevel { OK, CHECK, RECHECK, MISSING }

/** 이 신뢰도 이상이면 "확인됨"(초록). 영수증 자동 조건(스펙 7.5 — 모든 칸 초록)도 같은 값을 쓴다. */
const val CONFIDENCE_OK = 0.95

/** 이 신뢰도 이상이면 "확인 필요"(노랑), 미만이면 "다시 확인"(빨강). */
const val CONFIDENCE_CHECK = 0.75

fun confidenceLevelOf(value: String?, confidence: Double?): ConfidenceLevel = when {
    value == null -> ConfidenceLevel.MISSING
    confidence == null || confidence >= CONFIDENCE_OK -> ConfidenceLevel.OK
    confidence >= CONFIDENCE_CHECK -> ConfidenceLevel.CHECK
    else -> ConfidenceLevel.RECHECK
}

/** 사람이 봐야 하는 신뢰도인가 — "확인 필요"·"다시 확인". 값이 없는 칸(MISSING)은 경고가 아니라 빈 칸이다(채우라는 안내는 화면이 한 줄로). */
val ConfidenceLevel.needsCheck: Boolean get() = this == ConfidenceLevel.CHECK || this == ConfidenceLevel.RECHECK

/**
 * 디자인 언어 11장 ConfidenceRow — 고칠 수 있는 칸 한 줄: 왼쪽 이름, 오른쪽 값. 탭하면 편집.
 * **정상은 조용히**: 믿을 만한 값(OK)엔 아무 표시도 없다(예전엔 칸마다 "확인됨" — 여섯 번 반복돼 정작 볼 칸이 묻혔다, 2026-11-12).
 * 봐야 할 값만 색만으로 전하지 않게 아이콘 + 글자 + 테두리. 값이 없으면 [placeholder](흐린 글자)로 눌러서 넣는 칸임을 드러낸다.
 * [emphasized] = 화면의 주인공 값(결제 금액) — 값 글자를 한 단계 크게.
 */
@Composable
fun ConfidenceRow(
    label: String,
    value: String?,
    confidence: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    emphasized: Boolean = false,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val level = confidenceLevelOf(value, confidence)
    val warn =
        when (level) {
            ConfidenceLevel.CHECK -> Triple(c.warning, Icons.Rounded.ErrorOutline, R.string.confidence_check)
            ConfidenceLevel.RECHECK -> Triple(c.danger, Icons.Rounded.ReportProblem, R.string.confidence_recheck)
            ConfidenceLevel.OK, ConfidenceLevel.MISSING -> null
        }
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (emphasized) Size.buttonLg else Size.touchMin)
            .tappable(onClick)
            .clip(shape)
            .background(c.surface1)
            .then(warn?.let { Modifier.border(Size.ring, it.first, shape) } ?: Modifier)
            .padding(horizontal = Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Text(label, Modifier.weight(1f), style = t.body, color = c.textSecondary, maxLines = 1)
        warn?.let { (tone, icon, text) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(Size.icon))
                Text(stringResource(text), style = t.caption, color = tone)
            }
        }
        // 빈 칸 안내는 값보다 작게 — 값과 같은 크기면 칸마다 "눌러서 입력"이 소리친다
        Text(
            value ?: placeholder,
            style = if (value == null) {
                t.body
            } else if (emphasized) {
                t.headline
            } else {
                t.title
            },
            color = if (value == null) c.textTertiary else c.textPrimary,
            maxLines = 1,
        )
    }
}
