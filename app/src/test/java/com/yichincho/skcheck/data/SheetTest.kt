package com.yichincho.skcheck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetTest {
    private val picks = """{"ok":true,"updated_at":"2026-11-16 20:27:08","rows":[
      {"週別":"2026-11-02","結算日":"2026-11-13","代號":"6510","名稱":"精測","品質分":"94.9","理由":"破3萬9，選好股買進","EPS":"EPS 2026Q3 16.75（去年同期 8.41，YoY +99.2%）","買進價":"2305","最高價":"3505","最高報酬%":"52.1","現價":"3335","現報酬%":"44.7"},
      {"週別":"2026-11-09","結算日":"2026-11-20","代號":"2228","名稱":"劍麟","品質分":"51.6","理由":"前到前波低點","EPS":"","買進價":"64.9","最高價":"","最高報酬%":"","現價":"","現報酬%":""},
      {"週別":"2026-11-02","結算日":"2026-11-13","代號":"2330","名稱":"台積電","品質分":"95.9","理由":"","EPS":"","買進價":"2135","最高價":"2395","最高報酬%":"12.2","現價":"2350","現報酬%":"-1.5"}
    ]}"""
    private val stats = """{"ok":true,"rows":[{"週別":"2026-11-02","平均最高%":"30.6","平均現%":"25.1"}]}"""

    @Test
    fun groupsByWeekNewestFirst() {
        val r = Sheet.parse(picks, stats)
        assertEquals("2026-11-16 20:27:08", r.updatedAt)
        assertEquals(listOf("2026-11-09", "2026-11-02"), r.weeks.map { it.week })
        assertEquals(2, r.weeks[1].rows.size)
        assertEquals("平均最高 +30.6% · 現 +25.1%", r.weeks[1].summary)
        assertEquals("", r.weeks[0].summary)
    }

    @Test
    fun mapsColumnsToRow() {
        val w = Sheet.parse(picks).weeks[1]
        val r = w.rows[0]
        assertEquals("6510", r.code)
        assertEquals("94.9", r.score)
        assertEquals("2305", r.from)
        assertEquals("3335", r.to)
        assertEquals("+44.7%", r.pct)
        assertEquals(3335.0, r.toPrice!!, 0.0)
        assertEquals("-1.5%", w.rows[1].pct)
        // 還沒有收盤價時用買進價當基準
        val fresh = Sheet.parse(picks).weeks[0].rows[0]
        assertEquals("64.9", fresh.to)
        assertEquals("", fresh.from)
    }

    private val dupPicks = """{"ok":true,"rows":[
      {"週別":"2026-11-02","代號":"2330","名稱":"台積電","買進價":"2135","現價":"2350","現報酬%":"10.1"},
      {"週別":"2026-11-09","代號":"2330","名稱":"台積電","買進價":"2300","現價":"","現報酬%":""},
      {"週別":"2026-10-26","代號":"2330","名稱":"台積電","買進價":"2000","現價":"2350","現報酬%":"17.5"},
      {"週別":"2026-11-09","代號":"2228","名稱":"劍麟","買進價":"64.9"}
    ]}"""

    @Test
    fun duplicatesUseNewestBuyPrice() {
        val weeks = Sheet.parse(dupPicks).weeks
        assertEquals(3, Sheet.repeatCounts(weeks)["2330"])
        assertEquals(1, Sheet.repeatCounts(weeks)["2228"])
        val latest = Sheet.latestByCode(weeks)
        assertEquals("2026-11-09", latest["2330"]!!.first.week)
        assertEquals(2300.0, Sheet.buyPrice(latest["2330"]!!.second)!!, 0.0)
        // 有現價的列，買進價在 from
        assertEquals(2135.0, Sheet.buyPrice(weeks.first { it.week == "2026-11-02" }.rows[0])!!, 0.0)
    }

    @Test
    fun rebaseOnlyTouchesSheetOrShotBase() {
        val latest = Sheet.latestByCode(Sheet.parse(dupPicks).weeks)
        val rule = Rule("r", RuleType.DROP, 5.0, lastFiredDay = "2026-11-10", fireCount = 2)
        fun st(note: String, base: Double = 2135.0, prev: Boolean = false) =
            Stock("2330.TW", "台積電", base, prev, note, "", 0, listOf(rule))
        val moved = Sheet.rebase(st("選股表 2026-11-02"), latest)!!
        assertEquals(2300.0, moved.basePrice, 0.0)
        assertEquals("選股表 2026-11-09", moved.baseNote)
        assertEquals("", moved.rules[0].lastFiredDay)
        assertEquals(2300.0, Sheet.rebase(st("截圖價"), latest)!!.basePrice, 0.0)
        assertEquals(null, Sheet.rebase(st("自訂"), latest))
        assertEquals(null, Sheet.rebase(st("買進價"), latest))
        assertEquals(null, Sheet.rebase(st("選股表 2026-11-02", prev = true), latest))
        assertEquals(null, Sheet.rebase(st("選股表 2026-11-09", base = 2300.0), latest))
    }

    @Test
    fun snapshotKeepsNoteAndAdded() {
        val w = Sheet.parse(picks).weeks[1]
        val first = Sheet.toSnapshot(w, null)
        assertEquals("sheet-2026-11-02", first.id)
        assertEquals("", first.imageFile)
        assertTrue(Sheet.isSheetSnapshot(first))
        val again = Sheet.toSnapshot(w, first.copy(note = "我的想法", added = listOf("6510.TWO")))
        assertEquals("我的想法", again.note)
        assertEquals(listOf("6510.TWO"), again.added)
        assertEquals(first.createdAt, again.createdAt)
    }
}
