package com.yichincho.skcheck.data

/** 觀察清單的排序方式。 */
enum class WatchSort(val label: String) {
    ADDED("加入順序"),
    CODE("代號"),
    PRICE("股價"),
    DAY("今日漲跌"),
    BASE("基準漲跌"),
}

object Watch {
    /** 台股漲停 10%，報價四捨五入後約 9.9% 以上就算。 */
    const val LIMIT_UP_PCT = 9.85

    fun dayPct(s: Stock): Double? = if (s.prevClose > 0 && s.price > 0) changePct(s.price, s.prevClose) else null

    fun basePct(s: Stock): Double? = if (s.base > 0 && s.price > 0) changePct(s.price, s.base) else null

    fun isLimitUp(s: Stock): Boolean = (dayPct(s) ?: 0.0) >= LIMIT_UP_PCT

    /** 代號或名稱有包含 [query] 就留下（不分大小寫，忽略空白）。 */
    fun filter(list: List<Stock>, query: String): List<Stock> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { it.code.lowercase().contains(q) || it.name.lowercase().contains(q) || it.symbol.lowercase().contains(q) }
    }

    /** 排序；沒有報價的股票一律放最後面。[desc] 為 true 時大的在前。 */
    fun sort(list: List<Stock>, by: WatchSort, desc: Boolean): List<Stock> {
        fun <T : Comparable<T>> sortBy(key: (Stock) -> T?): List<Stock> {
            val (has, none) = list.partition { key(it) != null }
            val sorted = if (desc) has.sortedByDescending { key(it)!! } else has.sortedBy { key(it)!! }
            return sorted + none
        }
        return when (by) {
            WatchSort.ADDED -> if (desc) list.reversed() else list
            WatchSort.CODE -> sortBy { it.code }
            WatchSort.PRICE -> sortBy { s -> s.price.takeIf { it > 0 } }
            WatchSort.DAY -> sortBy(::dayPct)
            WatchSort.BASE -> sortBy(::basePct)
        }
    }

    /** 每種排序第一次按下時的方向：加入順序、代號照原本順序／由小到大，其他由大到小。 */
    fun defaultDesc(by: WatchSort) = by != WatchSort.CODE && by != WatchSort.ADDED
}
