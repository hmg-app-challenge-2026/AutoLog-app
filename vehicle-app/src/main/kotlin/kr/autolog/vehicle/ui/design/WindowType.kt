package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

/**
 * 창 크기([WindowLayout])에 따라 글자 크기가 달라지는 **역할** 모음(디자인 언어 6.2).
 * 넓은 창·분할 화면 한 칸은 같은 글자, 세로로 긴 창(TALL)만 일부가 다르다.
 * 화면·컴포넌트는 `LocalWindowLayout`으로 글자를 직접 고르지 않고 `AutoLogTheme.windowType.<역할>`을 쓴다 —
 * 창 크기별 글자 결정이 이 파일 한 곳에 모여, 표 하나만 보면 좁은 창에서 무엇이 한 단계 작아지는지 알 수 있다.
 * 값은 Pleos Connect v2.1.0 에뮬레이터(2560×1440 px, 160 dpi)에서 맞춘 것(PR #4, 2026-11-10 사용자 확인).
 */
@Immutable
data class WindowType(
    /** 전체 화면 제목("운전자를 선택해주세요"). */
    val screenTitle: TextStyle,
    /** 팝업 제목(`ModalTitle`). */
    val modalTitle: TextStyle,
    /** 운전자 타일의 이름. */
    val tileName: TextStyle,
    /** 동작 타일의 이름("새 운전자 추가") — 사람 이름보다 길어 한 단계 작다. */
    val tileAction: TextStyle,
    /** 타일 아래 보조 줄(부서). */
    val tileCaption: TextStyle,
    /** 하단 타일(`DockTile`) 이름. */
    val dockLabel: TextStyle,
    /** 운행 결과 맨 위 제목("운행 기록을 완료했어요") — 넓은·분할 화면은 화면 제목과 같은 `display`(2026-11-10 사용자 지시), 세로 창은 폭이 좁아 `title`. */
    val resultTitle: TextStyle,
    /** Home 기록 중 "운행 경로"의 출발지·목적지 — 넓은 화면 `display` 64 · 분할 화면 `headline` 52(2026-11-10 사용자 지시), 세로 창 `title`. */
    val homeRoute: TextStyle,

    /** 운행 결과 분류 문구("일반 업무용 거리 48.2 km로 분류돼요. 3초 후 자동 저장돼요."). */
    val resultClassification: TextStyle,
    /** 기간 이동 가운데 글자("2026년 11월" — 운행 기록 조회·운행기록부). */
    val periodTitle: TextStyle,
    /** 보조 화면 머리 줄 제목(`ScreenHeader` — "운행 기록", "운행기록부 내보내기"). */
    val headerTitle: TextStyle,
)

/** [layout]에 맞는 역할별 글자. 순수 함수 — `WindowTypeTest`가 표를 고정한다. */
fun windowTypeOf(layout: WindowLayout, t: AutoLogType): WindowType = when (layout) {
    // 넓은 창과 Pleos 1:1 분할 한 칸(≈1280×1268)은 높이가 같고 폭이 넉넉해 같은 글자
    WindowLayout.WIDE, WindowLayout.NARROW -> WindowType(
        screenTitle = t.display,
        modalTitle = t.headline,
        tileName = t.title,
        tileAction = t.label,
        tileCaption = t.caption,
        dockLabel = t.label,
        resultTitle = t.display,
        resultClassification = t.title,
        homeRoute = if (layout == WindowLayout.NARROW) t.headline else t.display,
        periodTitle = t.title,
        headerTitle = t.title,
    )
    WindowLayout.TALL -> WindowType(
        screenTitle = t.display,
        modalTitle = t.headline,
        tileName = t.title,
        tileAction = t.label,
        tileCaption = t.caption,
        dockLabel = t.label,
        resultTitle = t.title,
        resultClassification = t.label,
        homeRoute = t.title,
        periodTitle = t.title,
        headerTitle = t.title,
    )
}
