package kr.autolog.vehicle.ui.design.motion

import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.nav.TripResultMode

/** 백스택이 바뀔 때 화면이 움직이는 방식(디자인 언어 8.2). 화면 코드는 모르고 [navTransitionOf]가 스택 변화로 고른다. */
enum class NavTransition {
    /** 깊이 들어감 — 새 화면이 오른쪽에서, 뒤 화면은 왼쪽으로 조금 밀리며 어두워진다. */
    PUSH,

    /** 뒤로 — PUSH의 반대. 떠나는 화면이 위에서 오른쪽으로 빠진다. */
    POP,

    /** 위에 얹힘 — 아래에서 떠오른다(운행 직후 운행 결과). 뒤 화면은 그대로. */
    PRESENT,

    /** 얹힌 화면 내리기 — 아래로 내려가며 사라진다. */
    DISMISS,

    /** 같은 자리 교체 — 그 자리에서 교차(QR → 주유 입력 창, 처음 화면 바꾸기). */
    FADE_THROUGH,

    /** 맨 위가 같다 — 움직이지 않는다. */
    NONE,
}

/** 다른 화면 위에 "얹히는" 화면인가 — 운행 직후 운행 결과 카드(D-27·D-28). 이력에서 연 확인(REVIEW)은 보통 화면처럼 앞으로·뒤로. */
val Route.isPresented: Boolean get() = this is Route.TripResult && mode == TripResultMode.AFTER_TRIP

/**
 * [from] → [to] 스택 변화가 어떤 움직임인가(순수 함수, NavTransitionTest).
 * 얹힘이 먼저(띄우기·내리기) → 길어지면 앞으로 → 짧아졌고 새 맨 위가 원래 스택에 있었으면 뒤로(`replaceAll`로 처음 화면에 돌아가는 것도 뒤로)
 * → 그 밖(`replaceTop`, 처음 화면 바꾸기)은 같은 자리 교체.
 */
fun navTransitionOf(from: List<Route>, to: List<Route>): NavTransition {
    val fromTop = from.lastOrNull() ?: return NavTransition.NONE
    val toTop = to.lastOrNull() ?: return NavTransition.NONE
    return when {
        fromTop == toTop -> NavTransition.NONE
        toTop.isPresented && toTop !in from -> NavTransition.PRESENT
        fromTop.isPresented && fromTop !in to -> NavTransition.DISMISS
        to.size > from.size -> NavTransition.PUSH
        to.size < from.size && toTop in from -> NavTransition.POP
        else -> NavTransition.FADE_THROUGH
    }
}
