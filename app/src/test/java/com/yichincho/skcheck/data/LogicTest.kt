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

    @Test
    fun firesOncePerDay() {
        val today = "2026-10-19"
        val r = Rule("a", RuleType.DROP, 5.0)
        assertEquals(1, Rules.toFire(stock(1000.0, 1085.0, r), today).size)
        assertEquals(0, Rules.toFire(stock(1000.0, 1085.0, r.copy(lastFiredDay = today)), today).size)
        assertEquals(0, Rules.toFire(stock(1000.0, 1085.0, r.copy(enabled = false)), today).size)
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
        assertEquals(1085.0, q.prevClose, 0.0)
        assertEquals("2330.TW", q.symbol)
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
