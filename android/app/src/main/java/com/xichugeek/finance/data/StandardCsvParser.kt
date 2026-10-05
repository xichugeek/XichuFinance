package com.xichugeek.finance.data

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class CsvDocument(val headers: List<String>, val rows: List<List<String>>)

/** Structural precheck only; the Backend validates values, ownership and duplicates. */
object StandardCsvParser {
    const val MAX_BYTES = 512 * 1024
    const val MAX_ROWS = 500

    fun readLimited(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "CSV 最大为 512 KiB，请拆分文件" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun inspect(bytes: ByteArray): CsvDocument {
        require(bytes.size <= MAX_BYTES) { "CSV 最大为 512 KiB，请拆分文件" }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("请导出为 UTF-8 CSV")
        }
        require('\u0000' !in text) { "文件不是有效 CSV" }
        val records = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var fieldStarted = false
        var index = 0
        fun endField() {
            row.add(field.toString()); field.setLength(0)
            closedQuote = false; fieldStarted = false
        }
        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) records.add(row.toList())
            row.clear()
            require(records.size <= MAX_ROWS + 1) { "每次最多导入 500 行，请拆分文件" }
        }
        while (index < text.length) {
            val char = text[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') { field.append('"'); index++ }
                    else { quoted = false; closedQuote = true }
                } else field.append(char)
            } else when (char) {
                '"' -> { require(!fieldStarted && !closedQuote) { "CSV 引号格式不正确" }; quoted = true; fieldStarted = true }
                ',' -> endField()
                '\r', '\n' -> {
                    endRow()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> { require(!closedQuote) { "CSV 引号后只能是逗号或换行" }; field.append(char); fieldStarted = true }
            }
            index++
        }
        require(!quoted) { "CSV 引号未闭合" }
        if (fieldStarted || row.isNotEmpty()) endRow()
        require(records.isNotEmpty()) { "CSV 缺少表头" }
        val headers = records.first().map { it.trim() }
        val required = setOf("date", "description", "amount", "type", "account")
        require(headers.toSet().size == headers.size && headers.containsAll(required) && (headers.toSet() - required - "category").isEmpty()) {
            "表头应为 date,description,amount,type,account，可增加 category"
        }
        return CsvDocument(headers, records.drop(1))
    }
}
