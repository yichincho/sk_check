package com.yichincho.skcheck.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class CsvItem(
    val code: String,
    val name: String = "",
    val buyPrice: Double? = null,
    val sellPrice: Double? = null,
    val dropPct: Double? = null,
    val risePct: Double? = null,
    val note: String = "",
)

object Csv {
    const val TEMPLATE = "代號,名稱,目標買價,目標賣價,跌幅%,漲幅%,備註"

    /** Reads UTF-8 (with or without BOM); falls back to Big5, which Excel in Taiwan often saves. */
    fun decode(bytes: ByteArray): String {
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("Big5"))
        }
        return text.removePrefix("﻿")
    }

    /** Splits one CSV line, honouring double quotes. */
    fun splitLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out.add(cur.toString().trim()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString().trim())
        return out
    }

    private enum class Col { CODE, NAME, BUY, SELL, DROP, RISE, NOTE }

    private fun colFor(header: String): Col? {
        val h = header.trim().lowercase().replace(" ", "")
        return when {
            h in setOf("代號", "股票代號", "代碼", "code", "symbol", "ticker") -> Col.CODE
            h in setOf("名稱", "股票名稱", "name") -> Col.NAME
            "買" in h -> Col.BUY
            "賣" in h -> Col.SELL
            "跌" in h || h.startsWith("drop") -> Col.DROP
            "漲" in h || h.startsWith("rise") -> Col.RISE
            "備註" in h || "說明" in h || "note" in h -> Col.NOTE
            else -> null
        }
    }

    /** With a header row the columns can be in any order; without one they follow [TEMPLATE]. */
    fun parse(text: String): List<CsvItem> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val first = splitLine(lines[0])
        val headerCols = first.map(::colFor)
        val hasHeader = Col.CODE in headerCols
        val cols: List<Col?> = if (hasHeader) headerCols else Col.entries.toList()
        val dataLines = if (hasHeader) lines.drop(1) else lines
        val seen = mutableSetOf<String>()
        return dataLines.mapNotNull { line ->
            val cells = splitLine(line)
            fun cell(c: Col): String = cols.indexOf(c).let { if (it >= 0 && it < cells.size) cells[it] else "" }
            val code = Yahoo.normalizeCode(cell(Col.CODE))
            if (code.isEmpty() || !seen.add(code)) return@mapNotNull null
            CsvItem(
                code = code,
                name = cell(Col.NAME),
                buyPrice = parseNumber(cell(Col.BUY))?.takeIf { it > 0 },
                sellPrice = parseNumber(cell(Col.SELL))?.takeIf { it > 0 },
                dropPct = parseNumber(cell(Col.DROP))?.let { kotlin.math.abs(it) }?.takeIf { it > 0 },
                risePct = parseNumber(cell(Col.RISE))?.let { kotlin.math.abs(it) }?.takeIf { it > 0 },
                note = cell(Col.NOTE),
            )
        }
    }

    private fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun tradesToCsv(trades: List<Trade>): String = buildString {
        append('﻿')
        append("日期,代號,名稱,買賣,價格,股數,理由,說明\n")
        trades.sortedBy { it.date }.forEach { t ->
            append(listOf(t.date, t.symbol.substringBefore('.'), t.name, t.side.label, fmtNum(t.price), fmtNum(t.qty),
                t.tags.joinToString("、"), t.note).joinToString(",") { esc(it) })
            append('\n')
        }
    }
}
