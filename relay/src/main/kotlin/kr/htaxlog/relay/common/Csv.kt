package kr.htaxlog.relay.common

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** CSV 한 행. [line]은 파일의 줄 번호(헤더 = 1). 값은 헤더 이름(소문자·공백 제거)으로 찾는다. */
data class CsvRow(val line: Int, private val values: Map<String, String>) {
    operator fun get(column: String): String? = values[column]?.trim()?.ifEmpty { null }
}

/**
 * 관리자 CSV 업로드 (F-51~54). 첫 줄은 헤더.
 * 엑셀이 저장한 한국어 CSV는 CP949인 경우가 많아서, UTF-8로 못 읽으면 CP949로 읽는다. UTF-8 BOM은 버린다.
 */
object Csv {
    private val MS949: Charset = Charset.forName("MS949")

    fun parse(bytes: ByteArray): List<CsvRow> {
        val text = decode(bytes).removePrefix("﻿")
        val records = records(text)
        if (records.isEmpty()) return emptyList()
        val header = records.first().second.map { it.trim().lowercase().replace(" ", "_") }
        return records.drop(1).filter { (_, cells) -> cells.any { it.isNotBlank() } }.map { (line, cells) ->
            CsvRow(line, header.zip(cells + List(maxOf(0, header.size - cells.size)) { "" }).toMap())
        }
    }

    internal fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, MS949)
    }

    /** RFC 4180: 큰따옴표 안의 쉼표·줄바꿈, "" 이스케이프. (시작 줄 번호, 칸 목록) */
    private fun records(text: String): List<Pair<Int, List<String>>> {
        val out = mutableListOf<Pair<Int, List<String>>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var line = 1
        var recordLine = 1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> {
                    cells += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    cells += cell.toString()
                    cell.clear()
                    out += recordLine to cells
                    cells = mutableListOf()
                    line++
                    recordLine = line
                }
                else -> {
                    if (c == '\n') line++
                    cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || cells.isNotEmpty()) {
            cells += cell.toString()
            out += recordLine to cells
        }
        return out
    }
}
