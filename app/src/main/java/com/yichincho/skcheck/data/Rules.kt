package com.yichincho.skcheck.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")

fun newId(): String = UUID.randomUUID().toString().take(12)

fun todayTaipei(now: Instant = Instant.now()): String =
    now.atZone(TAIPEI).toLocalDate().toString()

object Market {
    /** Taiwan stock market, with a few minutes either side for Yahoo's delayed quotes to catch up. */
    private val OPEN = LocalTime.of(8, 55)
    private val CLOSE = LocalTime.of(13, 50)

    fun isOpen(now: ZonedDateTime = ZonedDateTime.now(TAIPEI)): Boolean {
        val t = now.withZoneSameInstant(TAIPEI)
        if (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) return false
        val time = t.toLocalTime()
        return !time.isBefore(OPEN) && time.isBefore(CLOSE)
    }
}

object Rules {
    /** The price at which [rule] fires, or null when it compares against a base price that isn't known yet. */
    fun line(rule: Rule, base: Double): Double? = when (rule.type) {
        RuleType.BELOW, RuleType.ABOVE -> rule.value
        RuleType.DROP -> if (base > 0) base * (1 - rule.value / 100) else null
        RuleType.RISE -> if (base > 0) base * (1 + rule.value / 100) else null
    }

    fun isHit(rule: Rule, price: Double, base: Double): Boolean {
        if (price <= 0) return false
        val line = line(rule, base) ?: return false
        // A hair of tolerance so a price exactly on the line counts despite floating-point rounding.
        val eps = 1e-9 * maxOf(1.0, line)
        return when (rule.type) {
            RuleType.BELOW, RuleType.DROP -> price <= line + eps
            RuleType.ABOVE, RuleType.RISE -> price >= line - eps
        }
    }

    /** Rules that should notify now: enabled, hit, and not already sent today. */
    fun toFire(stock: Stock, today: String): List<Rule> =
        stock.rules.filter { it.enabled && it.lastFiredDay != today && isHit(it, stock.price, stock.base) }

    fun title(rule: Rule): String = when (rule.type) {
        RuleType.BELOW -> "價格 ≤ ${fmtPrice(rule.value)}"
        RuleType.ABOVE -> "價格 ≥ ${fmtPrice(rule.value)}"
        RuleType.DROP -> "下跌 ${fmtNum(rule.value)}%"
        RuleType.RISE -> "上漲 ${fmtNum(rule.value)}%"
    }

    fun describe(rule: Rule, stock: Stock): String {
        val line = line(rule, stock.base)
        return when (rule.type) {
            RuleType.BELOW, RuleType.ABOVE -> title(rule)
            else -> title(rule) + if (line != null) "（提醒線 ${fmtPrice(line)}）" else "（等第一次報價）"
        }
    }

    /** Text for the notification and the journal, e.g. "跌幅達 5%：1,025（基準 1,085）". */
    fun firedText(rule: Rule, stock: Stock): String {
        val head = when (rule.type) {
            RuleType.BELOW -> "跌到 ${fmtPrice(rule.value)} 以下"
            RuleType.ABOVE -> "漲到 ${fmtPrice(rule.value)} 以上"
            RuleType.DROP -> "跌幅達 ${fmtNum(rule.value)}%"
            RuleType.RISE -> "漲幅達 ${fmtNum(rule.value)}%"
        }
        val base = if (stock.base > 0 && (rule.type == RuleType.DROP || rule.type == RuleType.RISE))
            "（基準 ${fmtPrice(stock.base)}，${fmtPct(changePct(stock.price, stock.base))}）" else ""
        return "$head：${fmtPrice(stock.price)}$base"
    }
}

fun changePct(price: Double, base: Double): Double = if (base > 0) (price - base) / base * 100 else 0.0

private val SYMBOLS = DecimalFormatSymbols(Locale.US)

fun fmtPrice(v: Double): String = DecimalFormat("#,##0.##", SYMBOLS).format(v)

fun fmtNum(v: Double): String = DecimalFormat("0.##", SYMBOLS).format(v)

fun fmtPct(v: Double): String {
    val s = DecimalFormat("0.0", SYMBOLS).format(kotlin.math.abs(v))
    return when {
        v > 0.04 -> "+$s%"
        v < -0.04 -> "−$s%"
        else -> "0.0%"
    }
}

fun fmtTime(epochSec: Long): String =
    if (epochSec <= 0) "—" else Instant.ofEpochSecond(epochSec).atZone(TAIPEI).format(DateTimeFormatter.ofPattern("M/d HH:mm"))

fun fmtDateMs(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(TAIPEI).format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
