package com.xichugeek.finance.data

import org.junit.Assert.*
import org.junit.Test

class StandardCsvParserTest {
    private val header = "date,description,amount,type,account"

    @Test fun readsBomQuotedCommasNewlinesAndEscapedQuotes() {
        val csv = "\uFEFF$header\r\n2026-09-01,\"早餐,咖啡\",0.10,expense,支付宝\r\n2026-09-02,\"虚构\n\"\"午餐\"\"\",0.20,expense,现金\r\n"
        val parsed = StandardCsvParser.inspect(csv.toByteArray())
        assertEquals(5, parsed.headers.size)
        assertEquals(2, parsed.rows.size)
        assertEquals("早餐,咖啡", parsed.rows[0][1])
        assertEquals("虚构\n\"午餐\"", parsed.rows[1][1])
        assertEquals("0.10", parsed.rows[0][2])
    }

    @Test fun rejectsMalformedEncodingHeadersQuotesAndLimits() {
        for (text in listOf("", "wrong,header", "$header,account\n", "$header\n2026-09-01,\"unclosed", "$header\n\"x\"oops", "$header\n\u0000")) {
            assertThrows(IllegalArgumentException::class.java) { StandardCsvParser.inspect(text.toByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) { StandardCsvParser.inspect(byteArrayOf(-1, -2)) }
        assertThrows(IllegalArgumentException::class.java) { StandardCsvParser.readLimited(ByteArray(StandardCsvParser.MAX_BYTES + 1).inputStream()) }
        val many = header + "\n" + "2026-09-01,早餐,1.00,expense,现金\n".repeat(501)
        assertThrows(IllegalArgumentException::class.java) { StandardCsvParser.inspect(many.toByteArray()) }
    }

    @Test fun keepsInvalidRowValuesForBackendPreviewAndSkipsBlankLines() {
        val parsed = StandardCsvParser.inspect("$header\n\n2026-09-01,bad amount,-1,expense,现金\nmissing,columns\n".toByteArray())
        assertEquals(2, parsed.rows.size)
        assertEquals("-1", parsed.rows[0][2])
        assertEquals(2, parsed.rows[1].size)
    }
}
