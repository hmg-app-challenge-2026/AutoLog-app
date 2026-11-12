package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space

/**
 * 디자인 언어 11장 SectionHeader — 한 화면 안 영역의 소제목("이번 달 운행 정보", "운행 경로"). 모든 섹션이 같은 모양:
 * `section`(38 sp 굵게), `textSecondary`, 아래 `space.3`. 크기와 굵기로 제목임을 드러낸다 —
 * 섹션 안에는 이보다 큰 회색 설명 글씨를 두지 않는다(데이터 값과 카드 안 목록 항목은 예외).
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.fillMaxWidth().padding(bottom = Space.s3),
        style = AutoLogTheme.type.section,
        color = AutoLogTheme.colors.textSecondary,
    )
}

/**
 * 소제목 + 내용 한 묶음. Home(모든 창 크기)·운행 결과 등 같은 정보를 보여 주는 화면이 같은 소제목 문구와 모양을 쓰게 한다
 * — 소제목 문구는 정보 종류마다 하나(`section_*` 문자열): 주행 거리 · 운행 경로 · 이번 달 운행 정보 · 최근 운행 기록.
 */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title)
        content()
    }
}
