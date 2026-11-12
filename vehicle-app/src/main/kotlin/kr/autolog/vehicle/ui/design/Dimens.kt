package kr.autolog.vehicle.ui.design

import androidx.compose.ui.unit.dp

/** 디자인 언어 6.4 (8 dp 그리드). 문서 space.N = Space.sN */
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp
    val s8 = 64.dp
    val s9 = 96.dp
    val tallMargin = 40.dp
}

/** 디자인 언어 6.5. */
object Size {
    val touchMin = 76.dp
    val buttonMd = 76.dp
    val buttonLg = 96.dp
    val buttonXl = 120.dp
    val iconButton = 96.dp
    val dockIcon = 112.dp // DockTile 아이콘 네모(넓은 창·분할 화면 한 칸)
    val dockIconTall = 96.dp
    val iconSm = 28.dp // 닫기(X)처럼 작은 원 안의 아이콘
    val statusDot = 12.dp // StatusLine 상태 점
    val routeDot = 12.dp // RouteFlow 출발 ○ · 도착 ● 표시
    val icon = 36.dp
    val iconLg = 48.dp
    val avatarChip = 48.dp
    val avatarSmall = 64.dp
    val avatarPreview = 120.dp // 팝업 아바타 미리보기(새 운전자 추가)
    val avatarPreviewTall = 160.dp // 같은 미리보기, 세로가 넉넉한 TALL
    val profileTileWide = 200.dp
    val profileTileNarrow = 160.dp
    val sheetMaxWidth = 960.dp
    val successBadge = 200.dp // 완료 표시(SuccessCheck) 원 지름 — 2560×1268 차량 화면 가운데에서 한눈에
    val actionButtonMaxWidth = 480.dp // 한 줄에 같은 폭으로 놓는 행동 버튼 하나의 최대 폭(운행 결과) — 2560 dp에서 버튼이 넓대대하지 않게
    val listActionMaxWidth = 400.dp // 목록 아래 가운데 버튼 하나의 최대 폭(운행 기록 조회 [운행기록부 내보내기], 2026-11-10 에뮬레이터 확인)
    val dialogMaxWidth = 640.dp // ModalCard 기본 최대 폭

    /** 화면 한 칸의 높이가 이 이상이면 차량 전체 화면(Pleos 에뮬레이터 앱 영역 ≈ 2560×1268 dp)처럼 넉넉한 창 — 간격을 넓힌다. */
    val spaciousHeight = 800.dp
    val hairline = 1.dp
    val progressBar = 8.dp
    val edgeBar = 4.dp // EdgeCountdownBar — 화면 맨 위 가는 남은 시간 막대
    val ring = 3.dp
}

/** 디자인 언어 6.6. */
object Radius {
    val sm = 14.dp
    val md = 20.dp
    val lg = 28.dp
    val xl = 40.dp
}
