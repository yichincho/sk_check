package com.yichincho.skcheck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class LogicTest {
    private fun stock(price: Double, base: Double, vararg rules: Rule) =
        Stock("2330.TW", "台積電", base, false, "加入時", "", 0, rules.toList(), price = price, prevClose = base)

    @Test
    fun dropRuleFiresAtTheLine() {
        val r = Rule("a", RuleType.DROP, 5.0)
        assertFalse(Rules.isHit(r, 1031.0, 1085.0))
        assertTrue(Rules.isHit(r, 1030.0, 1085.0))
    }

    @Test
    fun riseAndPriceRules() {
        assertTrue(Rules.isHit(Rule("a", RuleType.RISE, 8.0), 108.0, 100.0))
        assertFalse(Rules.isHit(Rule("a", RuleType.RISE, 8.0), 107.9, 100.0))
        assertTrue(Rules.isHit(Rule("b", RuleType.BELOW, 1000.0), 999.0, 0.0))
        assertTrue(Rules.isHit(Rule("c", RuleType.ABOVE, 1200.0), 1200.0, 0.0))
        // A percentage rule never fires without a base price.
        assertFalse(Rules.isHit(Rule("d", RuleType.DROP, 5.0), 1.0, 0.0))
    }

    // 2026-10-19 10:00 Taipei, a Monday.
    private val now = ZonedDateTime.parse("2026-10-19T10:00:00+08:00[Asia/Taipei]").toInstant()
    private val today = "2026-10-19"
    private val limits = Rules.Limits(gapHours = 1, maxPerDay = 3)

    private fun live(price: Double, low: Double, high: Double, vararg rules: Rule) =
        stock(price, 1085.0, *rules).copy(dayLow = low, dayHigh = high, quoteTime = now.epochSecond)

    @Test
    fun dayLowCountsEvenAfterTheBounce() {
        val r = Rule("a", RuleType.DROP, 5.0)
        // Price is back at 1,050 but the day's low of 1,028 crossed the 1,030.75 line.
        assertEquals(1, Rules.toFire(live(1050.0, 1028.0, 1060.0, r), today, now.toEpochMilli(), limits).size)
        assertEquals(0, Rules.toFire(live(1050.0, 1040.0, 1060.0, r), today, now.toEpochMilli(), limits).size)
        // Rising rules use the day's high.
        val up = Rule("b", RuleType.ABOVE, 1100.0)
        assertEquals(1, Rules.toFire(live(1050.0, 1040.0, 1101.0, up), today, now.toEpochMilli(), limits).size)
    }

    @Test
    fun hourlyAndThreeTimesADay() {
        val ms = now.toEpochMilli()
        var r = Rule("a", RuleType.DROP, 5.0)
        r = Rules.fired(r, today, ms)
        assertEquals(1, r.fireCount)
        // 30 minutes later: too soon.
        assertEquals(0, Rules.toFire(live(1000.0, 1000.0, 1000.0, r), today, ms + 30 * 60_000, limits).size)
        // An hour later: again.
        assertEquals(1, Rules.toFire(live(1000.0, 1000.0, 1000.0, r), today, ms + 60 * 60_000, limits).size)
        r = Rules.fired(Rules.fired(r, today, ms + 3_600_000), today, ms + 7_200_000)
        assertEquals(3, r.fireCount)
        // Third one sent: nothing more today, however long it waits.
        assertEquals(0, Rules.toFire(live(1000.0, 1000.0, 1000.0, r), today, ms + 10 * 3_600_000, limits).size)
        // Next day starts over.
        val tomorrow = now.plusSeconds(86_400)
        val s = live(1000.0, 1000.0, 1000.0, r).copy(quoteTime = tomorrow.epochSecond)
        assertEquals(1, Rules.toFire(s, "2026-10-20", tomorrow.toEpochMilli(), limits).size)
        assertEquals(1, Rules.fired(r, "2026-10-20", tomorrow.toEpochMilli()).fireCount)
    }

    @Test
    fun yesterdaysQuoteNeverFires() {
        val r = Rule("a", RuleType.DROP, 5.0)
        val s = live(1000.0, 1000.0, 1000.0, r).copy(quoteTime = now.epochSecond - 86_400)
        assertEquals(0, Rules.toFire(s, today, now.toEpochMilli(), limits).size)
        assertEquals(0, Rules.toFire(live(1000.0, 1000.0, 1000.0, r.copy(enabled = false)), today, now.toEpochMilli(), limits).size)
    }

    @Test
    fun marketHours() {
        assertTrue(Market.isOpen(ZonedDateTime.parse("2026-10-19T10:00:00+08:00[Asia/Taipei]")))
        assertFalse(Market.isOpen(ZonedDateTime.parse("2026-10-19T14:30:00+08:00[Asia/Taipei]")))
        assertFalse(Market.isOpen(ZonedDateTime.parse("2026-10-17T10:00:00+08:00[Asia/Taipei]")))
    }

    @Test
    fun parsesYahooChart() {
        val body = """{"chart":{"result":[{"meta":{"symbol":"2330.TW","shortName":"TAIWAN SEMICONDUCTOR","regularMarketPrice":1025.0,
            "chartPreviousClose":1085.0,"regularMarketTime":1792200000}}],"error":null}}"""
        val q = Yahoo.parse(body, "2330.TW")
        assertEquals(1025.0, q.price, 0.0)
        // No range in meta and no candles: the range is just the price.
        assertEquals(1025.0, q.dayLow, 0.0)
        assertEquals(1085.0, q.prevClose, 0.0)
        assertEquals("2330.TW", q.symbol)
    }

    @Test
    fun parsesDayRange() {
        val body = """{"chart":{"result":[{"meta":{"symbol":"2330.TW","regularMarketPrice":1040.0,"chartPreviousClose":1085.0,
            "regularMarketDayHigh":1062.0,"regularMarketDayLow":1028.0,"regularMarketTime":1792200000}}]}}"""
        val q = Yahoo.parse(body, "2330.TW")
        assertEquals(1062.0, q.dayHigh, 0.0)
        assertEquals(1028.0, q.dayLow, 0.0)
        val candles = """{"chart":{"result":[{"meta":{"regularMarketPrice":1040.0},
            "indicators":{"quote":[{"high":[1055.0,null,1061.0],"low":[1031.0,1027.5,null]}]}}]}}"""
        val c = Yahoo.parse(candles, "2330.TW")
        assertEquals(1061.0, c.dayHigh, 0.0)
        assertEquals(1027.5, c.dayLow, 0.0)
    }

    @Test
    fun picksGeminiModels() {
        val all = listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-image", "gemini-3-pro", "gemini-2.5-flash-lite",
            "text-embedding-004", "gemini-3-flash")
        val top = Gemini.pick(all)
        assertEquals("gemini-3-flash", top[0])
        assertEquals("gemini-2.5-flash", top[1])
        assertFalse(top.contains("gemini-flash-latest"))
        assertFalse(top.any { "image" in it || "embedding" in it })
    }

    @Test
    fun taiwanCodes() {
        assertTrue(Yahoo.isTaiwanCode("2330"))
        assertTrue(Yahoo.isTaiwanCode("00878"))
        assertTrue(Yahoo.isTaiwanCode("00632R"))
        assertFalse(Yahoo.isTaiwanCode("NVDA"))
    }

    @Test
    fun csvWithHeaderAndQuotes() {
        val text = "﻿代號,名稱,目標買價,目標賣價,跌幅%,漲幅%,備註\n2330,台積電,1000,,5,10,\"等法說, 再說\"\n2317,鴻海,190,230,,,\n2330,重複,,,,,"
        val items = Csv.parse(Csv.decode(text.toByteArray()))
        assertEquals(2, items.size)
        assertEquals("2330", items[0].code)
        assertEquals(1000.0, items[0].buyPrice!!, 0.0)
        assertEquals(5.0, items[0].dropPct!!, 0.0)
        assertEquals("等法說, 再說", items[0].note)
        assertEquals(230.0, items[1].sellPrice!!, 0.0)
        assertEquals(null, items[1].dropPct)
    }

    @Test
    fun csvInBig5() {
        val bytes = "代號,名稱\n2454,聯發科".toByteArray(charset("Big5"))
        val items = Csv.parse(Csv.decode(bytes))
        assertEquals("聯發科", items.single().name)
    }

    @Test
    fun numbersWithUnicodeMinus() {
        assertEquals(-3.36, parseNumber("(−3.36%)")!!, 1e-9)
        assertEquals(1025.5, parseNumber("1,025.5")!!, 1e-9)
        assertEquals(26.5, ShotRow("9904", "寶成", "", "", "25.05", "26.5", "+5.79%").toPrice!!, 1e-9)
    }

    @Test
    fun parsesShotReply() {
        val reply = """好的：```json
            {"title":"2026-10-05 ~ 結算 2026-10-16","summary":"未結算，平均 +0.07%","rows":[
            {"code":"9904.TW","name":"寶成","reason":"雙勢向上","eps":"EPS 2026Q2 1.61","from":"25.05","to":"26.5","pct":"+5.79%"},
            {"code":"","name":"沒有代號","reason":"","eps":"","from":"","to":"","pct":""}]}```"""
        val r = Nvidia.parseShot(reply)
        assertEquals(1, r.rows.size)
        assertEquals("9904", r.rows[0].code)
        assertEquals("未結算，平均 +0.07%", r.summary)
    }

    @Test
    fun picksVisionModels() {
        val all = listOf(
            "meta/llama-3.2-90b-vision-instruct", "nvidia/nv-embedqa-e5-v5", "deepseek-ai/deepseek-v4.1",
            "meta/llama-4-maverick-17b-128e-instruct", "microsoft/phi-4-multimodal-instruct", "nvidia/nemoretriever-parse",
            "google/gemma-3-27b-it", "meta/llama-3.2-11b-vision-instruct", "qwen/qwen3-vl-235b-a22b-instruct",
        )
        val top = Nvidia.topVision(all)
        assertEquals(5, top.size)
        assertEquals("qwen/qwen3-vl-235b-a22b-instruct", top[0])
        assertEquals("meta/llama-4-maverick-17b-128e-instruct", top[1])
        assertFalse(top.any { "embed" in it || "deepseek" in it || "parse" in it })
    }
}
