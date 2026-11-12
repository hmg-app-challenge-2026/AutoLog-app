package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kr.autolog.vehicle.ui.design.AutoLogTheme

/**
 * 디자인 언어 11장 ButtonNote — **버튼 바로 위 설명글**(단계 안내·실패 이유·"먼저 차량을 연결해 주세요" 같은 한두 줄).
 * 늘 화면(칸) 폭 전체에 **가운데 정렬** — 버튼 글자와 같은 세로축에 읽히게(2026-11-10 사용자 지시, 전 화면 통일).
 * 팝업 안은 같은 규칙의 `ModalText`를 쓴다.
 */
@Composable
fun ButtonNote(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AutoLogTheme.type.body,
    color: Color = AutoLogTheme.colors.textSecondary,
) {
    Text(text, modifier.fillMaxWidth(), style = style, color = color, textAlign = TextAlign.Center)
}
