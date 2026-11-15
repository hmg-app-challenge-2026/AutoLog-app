package kr.htaxlog.relay.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class CsvTest {
    @Test
    fun `헤더 이름으로 값을 찾고 줄 번호를 남긴다`() {
        val rows = Csv.parse("name,dept,email\n김영업,영업1팀,kim@example.com\n이현장,,\n".toByteArray())

        assertEquals(2, rows.size)
        assertEquals("김영업", rows[0]["name"])
        assertEquals(2, rows[0].line)
        assertNull(rows[1]["dept"])
        assertEquals(3, rows[1].line)
    }

    @Test
    fun `큰따옴표 안의 쉼표·줄바꿈과 이스케이프를 읽는다`() {
        val rows = Csv.parse("label,address\n\"본사, 2층\",\"서울 \"\"강남\"\"\n테헤란로\"\n다음,x\n".toByteArray())

        assertEquals("본사, 2층", rows[0]["label"])
        assertEquals("서울 \"강남\"\n테헤란로", rows[0]["address"])
        assertEquals(4, rows[1].line)
    }

    @Test
    fun `UTF-8 BOM과 CRLF, 빈 줄을 처리한다`() {
        val rows = Csv.parse("﻿Email , Name\r\na@x.com,가\r\n\r\n".toByteArray())

        assertEquals("a@x.com", rows.single()["email"])
    }

    @Test
    fun `엑셀이 저장한 CP949 CSV도 읽는다`() {
        val bytes = "이름,부서\n김영업,영업1팀\n".toByteArray(Charset.forName("MS949"))

        assertEquals("영업1팀", Csv.parse(bytes).single()["부서"])
    }
}
