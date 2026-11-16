package com.yichincho.skcheck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSortTest {
    private fun st(code: String, name: String, price: Double, prev: Double, base: Double = 0.0) =
        Stock("$code.TW", name, base, false, "", "", 0, emptyList(), price = price, prevClose = prev)

    private val list = listOf(
        st("2330", "台積電", 1000.0, 990.0, base = 800.0),   // 今日 +1.0%，基準 +25%
        st("2317", "鴻海", 220.0, 200.0, base = 230.0),      // 今日 +10%（漲停），基準 -4.3%
        st("6488", "環球晶", 0.0, 0.0),                      // 沒報價
        st("2454", "聯發科", 1500.0, 1530.0, base = 1400.0), // 今日 -2.0%
    )

    @Test
    fun sortsByCodePriceAndChange() {
        assertEquals(listOf("2317", "2330", "2454", "6488"), Watch.sort(list, WatchSort.CODE, false).map { it.code })
        assertEquals(listOf("2454", "2330", "2317", "6488"), Watch.sort(list, WatchSort.PRICE, true).map { it.code })
        // 漲停的排最上面，沒報價的永遠在最後
        assertEquals(listOf("2317", "2330", "2454", "6488"), Watch.sort(list, WatchSort.DAY, true).map { it.code })
        assertEquals(listOf("2454", "2330", "2317", "6488"), Watch.sort(list, WatchSort.DAY, false).map { it.code })
        assertEquals(listOf("2330", "2454", "2317", "6488"), Watch.sort(list, WatchSort.BASE, true).map { it.code })
        assertEquals(list, Watch.sort(list, WatchSort.ADDED, false))
    }

    @Test
    fun searchByCodeOrName() {
        assertEquals(listOf("2330"), Watch.filter(list, "台積").map { it.code })
        assertEquals(listOf("2330"), Watch.filter(list, " 2330 ").map { it.code })
        assertEquals(listOf("2330", "2317"), Watch.filter(list, "23").map { it.code })
        assertEquals(4, Watch.filter(list, "").size)
    }

    @Test
    fun limitUp() {
        assertTrue(Watch.isLimitUp(list[1]))
        assertFalse(Watch.isLimitUp(list[0]))
        assertFalse(Watch.isLimitUp(list[2]))
    }
}
