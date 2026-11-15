package kr.htaxlog.vehicle.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 최소 XLSX 작성기 (F-21). Apache POI 대신 SpreadsheetML XML을 Zip으로 묶는다 — APK 크기·속도 때문(기술명세 3.5 대체안).
 * 셀 수식은 쓰지 않는다(값만). 문자열은 inline string이라 sharedStrings가 필요 없다. 순수 JVM.
 */
class XlsxWriter {
    /** 셀 값. [Num]은 숫자 서식, [Text]는 문자열. [bold]면 굵게(머리글). */
    sealed interface Cell {
        val bold: Boolean

        data class Text(val value: String, override val bold: Boolean = false) : Cell

        data class Num(val value: Double, val decimals: Int = 1, override val bold: Boolean = false) : Cell
    }

    data class Sheet(
        val name: String,
        val rows: List<List<Cell?>>,
        /** 열 너비(문자 수). 없으면 기본. */
        val widths: List<Int> = emptyList(),
        /** 병합할 영역, 예: "A1:K1". */
        val merges: List<String> = emptyList(),
    )

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
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append(HEAD).append("<worksheet xmlns=\"$NS\" xmlns:r=\"$NS_R\">")
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.forEachIndexed { i, w ->
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, cell ->
                if (cell != null) append(cellXml(ref(c, r), cell))
            }
            append("</row>")
        }
        append("</sheetData>")
        if (s.merges.isNotEmpty()) {
            append("<mergeCells count=\"${s.merges.size}\">")
            s.merges.forEach { append("<mergeCell ref=\"${escape(it)}\"/>") }
            append("</mergeCells>")
        }
        append("</worksheet>")
    }

    private fun cellXml(ref: String, cell: Cell): String = when (cell) {
        is Cell.Text -> {
            val style = if (cell.bold) " s=\"1\"" else ""
            "<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${escape(cell.value)}</t></is></c>"
        }
        is Cell.Num -> {
            val style = when {
                cell.bold -> 3
                cell.decimals == 0 -> 4
                else -> 2
            }
            "<c r=\"$ref\" s=\"$style\"><v>${cell.value}</v></c>"
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

        /** 0 기본, 1 굵게, 2 숫자 0.0, 3 굵은 숫자 0.0, 4 숫자 #,##0. */
        private const val STYLES = HEAD + "<styleSheet xmlns=\"$NS\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.0\"/></numFmts>" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"5\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" " +
            "applyNumberFormat=\"1\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "</cellXfs></styleSheet>"

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
