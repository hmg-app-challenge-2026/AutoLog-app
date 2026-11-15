package kr.htaxlog.vehicle.mail

import kr.htaxlog.vehicle.core.ReportFile
import java.time.YearMonth
import java.util.Locale

/** 운행기록부 메일의 제목·본문·첨부 이름 (F-22). 순수 함수라 JVM 테스트가 된다. 본문은 사용자가 입력하지 않는다. */
object MailComposer {
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /**
     * 차량번호를 모를 때 앱 A(ReportBuilder)가 넣는 자리표시. 받는 사람에게 의미가 없어 메일에는 쓰지 않는다.
     * 서버에 연결하지 않았거나 서버에 번호판이 없을 때 생긴다.
     */
    private const val UNKNOWN_PLATE = "미등록"

    /** 예: "[H-TaxLog] 2026년 11월 운행기록부 · 12가3456" (번호판을 모르면 뒷부분 없음) */
    fun subject(r: ReportFile): String = "[H-TaxLog] ${monthKo(r.month)} 운행기록부" + (plate(r)?.let { " · $it" } ?: "")

    fun body(r: ReportFile): String = buildString {
        val who = plate(r)?.let { "$it 차량의 " } ?: ""
        appendLine("${monthKo(r.month)} ${who}운행기록부를 보내드립니다.")
        appendLine()
        appendLine("- 운행 ${r.tripCount}건")
        appendLine("- 총 주행거리 ${String.format(Locale.ROOT, "%,.1f", r.totalKm)} km")
        if (r.reviewCount > 0) {
            appendLine("- 검토 필요 ${r.reviewCount}건 (첨부 엑셀의 \"검토\" 열을 확인해 주세요)")
        } else {
            appendLine("- 검토 필요 없음")
        }
        appendLine()
        append("이 메일은 차량의 H-TaxLog 앱이 자동으로 작성했습니다.")
    }

    /** 예: "운행기록부_2026-11_12가3456.xlsx" (번호판을 모르면 "운행기록부_2026-11.xlsx") */
    fun attachmentName(r: ReportFile): String =
        "운행기록부_${r.month}" + (plate(r)?.let { "_${it.replace(" ", "")}" } ?: "") + ".xlsx"

    /** 받는 사람에게 보여 줄 번호판. 비었거나 자리표시면 null. */
    internal fun plate(r: ReportFile): String? = r.plateNo.trim().takeIf { it.isNotEmpty() && it != UNKNOWN_PLATE }

    private fun monthKo(m: YearMonth) = "${m.year}년 ${m.monthValue}월"
}
