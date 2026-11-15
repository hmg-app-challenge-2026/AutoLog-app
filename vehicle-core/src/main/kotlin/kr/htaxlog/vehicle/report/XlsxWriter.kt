package kr.htaxlog.vehicle.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 최소 XLSX 작성기 (F-21). Apache POI 대신 SpreadsheetML XML을 Zip으로 묶는다 — APK 크기·속도 때문(기술명세 3.5 대체안).
 * 셀 수식은 쓰지 않는다(값만). 문자열은 inline string이라 sharedStrings가 필요 없다. 순수 JVM.
 */
class XlsxWriter {
    /** 칸 모양: 테두리·가운데 정렬·회색 바탕·글자 크기·줄바꿈. 국세청 별지 서식처럼 표를 그릴 때 쓴다. */
    data class Look(
        val border: Boolean = false,
        val center: Boolean = false,
        val gray: Boolean = false,
        val size: Int = 11,
        val wrap: Boolean = false,
    )

    /** 셀 값. [Num]은 숫자 서식, [Text]는 문자열. [bold]면 굵게(머리글). */
    sealed interface Cell {
        val bold: Boolean
        val look: Look

        data class Text(val value: String, override val bold: Boolean = false, override val look: Look = Look()) : Cell

        data class Num(
            val value: Double,
            val decimals: Int = 1,
            override val bold: Boolean = false,
            override val look: Look = Look(),
        ) : Cell
    }

    data class Sheet(
        val name: String,
        val rows: List<List<Cell?>>,
        /** 열 너비(문자 수). 없으면 기본. */
        val widths: List<Int> = emptyList(),
        /** 병합할 영역, 예: "A1:K1". */
        val merges: List<String> = emptyList(),
        /** 행 높이(포인트), 0-기반 행 번호 → 높이. */
        val heights: Map<Int, Double> = emptyMap(),
        /** 인쇄를 가로로, 한 쪽 너비에 맞춘다. */
        val landscape: Boolean = false,
    )

    /** 셀 서식 하나 = (숫자 형식, 굵게, 모양). 시트들이 쓰는 것만 styles.xml에 넣는다. */
    private data class StyleKey(val numFmt: Int, val bold: Boolean, val look: Look)

    private class Styles(sheets: List<Sheet>) {
        val keys: List<StyleKey> = (
            listOf(StyleKey(0, false, Look())) +
                sheets.flatMap { s -> s.rows.flatten().filterNotNull().map { keyOf(it) } }
            ).distinct()
        private val index = keys.withIndex().associate { (i, k) -> k to i }
        private val fonts = keys.map { it.bold to it.look.size }.distinct()

        fun of(cell: Cell): Int = index.getValue(keyOf(cell))

        fun xml(): String = buildString {
            append(HEAD).append("<styleSheet xmlns=\"$NS\">")
            append("<numFmts count=\"2\"><numFmt numFmtId=\"$FMT_1\" formatCode=\"#,##0.0\"/>")
            append("<numFmt numFmtId=\"$FMT_2\" formatCode=\"#,##0.00\"/></numFmts>")
            append("<fonts count=\"${fonts.size}\">")
            fonts.forEach { (b, sz) ->
                append("<font>").append(if (b) "<b/>" else "").append("<sz val=\"$sz\"/><name val=\"맑은 고딕\"/></font>")
            }
            append("</fonts>")
            append("<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>")
            append("<fill><patternFill patternType=\"gray125\"/></fill>")
            append(
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFBFBFBF\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>",
            )
            append("<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>")
            append("<border>")
            listOf("left", "right", "top", "bottom").forEach { append("<$it style=\"thin\"><color auto=\"1\"/></$it>") }
            append("<diagonal/></border></borders>")
            append(
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>",
            )
            append("<cellXfs count=\"${keys.size}\">")
            keys.forEach { k ->
                val font = fonts.indexOf(k.bold to k.look.size)
                val fill = if (k.look.gray) 2 else 0
                val border = if (k.look.border) 1 else 0
                append(
                    "<xf numFmtId=\"${k.numFmt}\" fontId=\"$font\" fillId=\"$fill\" borderId=\"$border\" xfId=\"0\" " +
                        "applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"",
                )
                if (k.look.center || k.look.wrap) {
                    append(" applyAlignment=\"1\"><alignment")
                    if (k.look.center) append(" horizontal=\"center\"")
                    append(" vertical=\"center\"")
                    if (k.look.wrap) append(" wrapText=\"1\"")
                    append("/></xf>")
                } else {
                    append("/>")
                }
            }
            append("</cellXfs></styleSheet>")
        }

        private fun keyOf(cell: Cell) = StyleKey(
            when (cell) {
                is Cell.Text -> 0
                is Cell.Num -> when (cell.decimals) {
                    0 -> FMT_0
                    2 -> FMT_2
                    else -> FMT_1
                }
            },
            cell.bold,
            cell.look,
        )
    }

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "sheet required" }
        ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            val styles = Styles(sheets)
            put("xl/styles.xml", styles.xml())
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s, styles)) }
        }
    }

    private fun sheetXml(s: Sheet, styles: Styles): String = buildString {
        append(HEAD).append("<worksheet xmlns=\"$NS\" xmlns:r=\"$NS_R\">")
        if (s.landscape) append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.forEachIndexed { i, w ->
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            val height = s.heights[r]?.let { " ht=\"$it\" customHeight=\"1\"" }.orEmpty()
            append("<row r=\"${r + 1}\"$height>")
            row.forEachIndexed { c, cell ->
                if (cell != null) append(cellXml(ref(c, r), cell, styles.of(cell)))
            }
            append("</row>")
        }
        append("</sheetData>")
        if (s.merges.isNotEmpty()) {
            append("<mergeCells count=\"${s.merges.size}\">")
            s.merges.forEach { append("<mergeCell ref=\"${escape(it)}\"/>") }
            append("</mergeCells>")
        }
        if (s.landscape) {
            append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.3\" footer=\"0.3\"/>")
            append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
        }
        append("</worksheet>")
    }

    private fun cellXml(ref: String, cell: Cell, style: Int): String {
        val s = if (style != 0) " s=\"$style\"" else ""
        return when (cell) {
            is Cell.Text -> "<c r=\"$ref\" t=\"inlineStr\"$s><is><t xml:space=\"preserve\">${escape(
                cell.value,
            )}</t></is></c>"
            is Cell.Num -> "<c r=\"$ref\"$s><v>${cell.value}</v></c>"
        }
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(HEAD).append("<workbook xmlns=\"$NS\" xmlns:r=\"$NS_R\"><sheets>")
        sheets.forEachIndexed { i, s ->
            append("<sheet name=\"${escape(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append(HEAD).append("<Relationships xmlns=\"$NS_PKG\">")
        for (i in 1..n) {
            append("<Relationship Id=\"rId$i\" Type=\"$NS_R/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${n + 1}\" Type=\"$NS_R/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun contentTypes(n: Int) = buildString {
        append(HEAD).append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"$CT.sheet.main+xml\"/>")
        for (i in 1..n) append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"$CT.worksheet+xml\"/>")
        append(
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>",
        )
        append("</Types>")
    }

    companion object {
        private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val NS_PKG = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val CT = "application/vnd.openxmlformats-officedocument.spreadsheetml"

        private const val ROOT_RELS = HEAD + "<Relationships xmlns=\"$NS_PKG\">" +
            "<Relationship Id=\"rId1\" Type=\"$NS_R/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

        private const val FMT_0 = 3 // 내장 #,##0
        private const val FMT_1 = 164 // #,##0.0
        private const val FMT_2 = 165 // #,##0.00

        /** 0-기반 열·행 → "A1". */
        fun ref(col: Int, row: Int): String {
            var c = col
            val sb = StringBuilder()
            do {
                sb.insert(0, 'A' + c % 26)
                c = c / 26 - 1
            } while (c >= 0)
            return "$sb${row + 1}"
        }

        fun escape(s: String): String = buildString(s.length) {
            for (ch in s) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= ' ') append(ch)
                }
            }
        }
    }
}
