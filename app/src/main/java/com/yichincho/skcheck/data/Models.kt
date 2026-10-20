package com.yichincho.skcheck.data

import org.json.JSONArray
import org.json.JSONObject

enum class RuleType(val label: String) {
    BELOW("價格 ≤"),
    ABOVE("價格 ≥"),
    DROP("下跌"),
    RISE("上漲"),
}

/**
 * One alert on one stock. [lastFiredDay] (yyyy-MM-dd, Taipei), [fireCount] (notifications sent that day) and
 * [lastFiredAt] (epoch ms) limit how often it notifies; a different day means the count starts again.
 */
data class Rule(
    val id: String,
    val type: RuleType,
    val value: Double,
    val enabled: Boolean = true,
    val lastFiredDay: String = "",
    val fireCount: Int = 0,
    val lastFiredAt: Long = 0,
) {
    fun firesToday(today: String) = if (lastFiredDay == today) fireCount.coerceAtLeast(1) else 0
}

data class Stock(
    /** Yahoo symbol, e.g. 2330.TW or 6488.TWO. */
    val symbol: String,
    val name: String,
    /** Price the percentage rules compare against; 0 means "not known yet", filled from the first quote. */
    val basePrice: Double,
    /** When true the percentage rules compare against yesterday's close instead of [basePrice]. */
    val baseIsPrevClose: Boolean,
    val baseNote: String,
    val note: String,
    val addedAt: Long,
    val rules: List<Rule>,
    val price: Double = 0.0,
    val prevClose: Double = 0.0,
    /** Epoch seconds of the quote Yahoo returned. */
    val quoteTime: Long = 0,
    val quoteError: String = "",
    /** Highest and lowest prices of the session the quote belongs to; 0 when Yahoo didn't give them. */
    val dayHigh: Double = 0.0,
    val dayLow: Double = 0.0,
) {
    val code: String get() = symbol.substringBefore('.')

    /** The price the percentage rules use right now. */
    val base: Double get() = if (baseIsPrevClose) prevClose else basePrice
}

data class ShotRow(
    val code: String,
    val name: String,
    val reason: String,
    val eps: String,
    val from: String,
    val to: String,
    val pct: String,
    /** 品質分數 shown next to the name on the screenshot (0–100), e.g. "94.4". */
    val score: String = "",
) {
    /** The latest price on the screenshot, used as the starting point for alerts. */
    val toPrice: Double? get() = parseNumber(to)
    val scoreValue: Double? get() = parseNumber(score)
}

/** A saved screenshot: the picture, what the AI read from it, and the user's own note. */
data class Snapshot(
    val id: String,
    val createdAt: Long,
    val imageFile: String,
    val title: String,
    val summary: String,
    val note: String,
    val model: String,
    val rows: List<ShotRow>,
    /** Symbols that were added to the watchlist from this screenshot. */
    val added: List<String>,
)

/** One screenshot that contains a stock, with that stock's row. */
data class ShotHit(val shot: Snapshot, val row: ShotRow)

/** Every screenshot that has [code], newest first. */
fun shotHistory(snapshots: List<Snapshot>, code: String): List<ShotHit> =
    snapshots.sortedByDescending { it.createdAt }.mapNotNull { sh -> sh.rows.firstOrNull { it.code == code }?.let { ShotHit(sh, it) } }

/** The row for [code] on the newest screenshot that has it. */
fun latestShotRow(snapshots: List<Snapshot>, code: String): ShotRow? = shotHistory(snapshots, code).firstOrNull()?.row

/** The score from the newest screenshot that has one; empty if none do. */
fun List<ShotHit>.latestScore(): String = firstOrNull { it.row.score.isNotBlank() }?.row?.score.orEmpty()

/** Fills the blank fields of [old] from the same-code row in [fresh]; anything already filled stays as the user left it. */
fun mergeRows(old: List<ShotRow>, fresh: List<ShotRow>): List<ShotRow> = old.map { o ->
    val f = fresh.firstOrNull { it.code == o.code } ?: return@map o
    o.copy(
        reason = o.reason.ifBlank { f.reason },
        eps = o.eps.ifBlank { f.eps },
        from = o.from.ifBlank { f.from },
        to = o.to.ifBlank { f.to },
        pct = o.pct.ifBlank { f.pct },
        score = o.score.ifBlank { f.score },
    )
}

enum class Side(val label: String) { BUY("買進"), SELL("賣出") }

data class Trade(
    val id: String,
    val symbol: String,
    val name: String,
    val side: Side,
    val price: Double,
    val qty: Double,
    val date: String,
    val tags: List<String>,
    val note: String,
    val createdAt: Long,
)

/** A notification that was sent. */
data class AlertEvent(
    val id: String,
    val symbol: String,
    val name: String,
    val text: String,
    val price: Double,
    val time: Long,
)

/** "1,025.5" / "+5.79%" / "(−3.36%)" → number; null if there is none. Handles the Unicode minus sign. */
fun parseNumber(s: String): Double? {
    val clean = s.replace('−', '-').replace('－', '-').replace(",", "").replace("%", "")
        .replace("(", "").replace(")", "").replace("（", "").replace("）", "").trim()
    val m = Regex("""[-+]?\d+(\.\d+)?""").find(clean) ?: return null
    return m.value.toDoubleOrNull()
}

// ---------- JSON ----------

inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(f) }

fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }

fun List<String>.toJson(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

fun Rule.toJson(): JSONObject = JSONObject()
    .put("id", id).put("type", type.name).put("value", value)
    .put("enabled", enabled).put("lastFiredDay", lastFiredDay).put("fireCount", fireCount).put("lastFiredAt", lastFiredAt)

fun ruleFromJson(o: JSONObject) = Rule(
    id = o.optString("id"),
    type = runCatching { RuleType.valueOf(o.optString("type")) }.getOrDefault(RuleType.BELOW),
    value = o.optDouble("value", 0.0),
    enabled = o.optBoolean("enabled", true),
    lastFiredDay = o.optString("lastFiredDay"),
    fireCount = o.optInt("fireCount", if (o.optString("lastFiredDay").isNotEmpty()) 1 else 0),
    lastFiredAt = o.optLong("lastFiredAt", 0),
)

fun Stock.toJson(): JSONObject = JSONObject()
    .put("symbol", symbol).put("name", name).put("basePrice", basePrice).put("baseIsPrevClose", baseIsPrevClose)
    .put("baseNote", baseNote).put("note", note).put("addedAt", addedAt)
    .put("rules", JSONArray().also { a -> rules.forEach { a.put(it.toJson()) } })
    .put("price", price).put("prevClose", prevClose).put("quoteTime", quoteTime).put("quoteError", quoteError)
    .put("dayHigh", dayHigh).put("dayLow", dayLow)

fun stockFromJson(o: JSONObject) = Stock(
    symbol = o.optString("symbol"),
    name = o.optString("name"),
    basePrice = o.optDouble("basePrice", 0.0),
    baseIsPrevClose = o.optBoolean("baseIsPrevClose", false),
    baseNote = o.optString("baseNote"),
    note = o.optString("note"),
    addedAt = o.optLong("addedAt"),
    rules = (o.optJSONArray("rules") ?: JSONArray()).mapObjects(::ruleFromJson),
    price = o.optDouble("price", 0.0),
    prevClose = o.optDouble("prevClose", 0.0),
    quoteTime = o.optLong("quoteTime"),
    quoteError = o.optString("quoteError"),
    dayHigh = o.optDouble("dayHigh", 0.0),
    dayLow = o.optDouble("dayLow", 0.0),
)

fun ShotRow.toJson(): JSONObject = JSONObject()
    .put("code", code).put("name", name).put("reason", reason).put("eps", eps)
    .put("from", from).put("to", to).put("pct", pct).put("score", score)

fun shotRowFromJson(o: JSONObject) = ShotRow(
    code = o.optString("code").trim(),
    name = o.optString("name").trim(),
    reason = o.optString("reason").trim(),
    eps = o.optString("eps").trim(),
    from = o.optString("from").trim(),
    to = o.optString("to").trim(),
    pct = o.optString("pct").trim(),
    score = o.optString("score").trim(),
)

fun Snapshot.toJson(): JSONObject = JSONObject()
    .put("id", id).put("createdAt", createdAt).put("imageFile", imageFile).put("title", title)
    .put("summary", summary).put("note", note).put("model", model)
    .put("rows", JSONArray().also { a -> rows.forEach { a.put(it.toJson()) } })
    .put("added", added.toJson())

fun snapshotFromJson(o: JSONObject) = Snapshot(
    id = o.optString("id"),
    createdAt = o.optLong("createdAt"),
    imageFile = o.optString("imageFile"),
    title = o.optString("title"),
    summary = o.optString("summary"),
    note = o.optString("note"),
    model = o.optString("model"),
    rows = (o.optJSONArray("rows") ?: JSONArray()).mapObjects(::shotRowFromJson),
    added = (o.optJSONArray("added") ?: JSONArray()).strings(),
)

fun Trade.toJson(): JSONObject = JSONObject()
    .put("id", id).put("symbol", symbol).put("name", name).put("side", side.name).put("price", price)
    .put("qty", qty).put("date", date).put("tags", tags.toJson()).put("note", note).put("createdAt", createdAt)

fun tradeFromJson(o: JSONObject) = Trade(
    id = o.optString("id"),
    symbol = o.optString("symbol"),
    name = o.optString("name"),
    side = runCatching { Side.valueOf(o.optString("side")) }.getOrDefault(Side.BUY),
    price = o.optDouble("price", 0.0),
    qty = o.optDouble("qty", 0.0),
    date = o.optString("date"),
    tags = (o.optJSONArray("tags") ?: JSONArray()).strings(),
    note = o.optString("note"),
    createdAt = o.optLong("createdAt"),
)

fun AlertEvent.toJson(): JSONObject = JSONObject()
    .put("id", id).put("symbol", symbol).put("name", name).put("text", text).put("price", price).put("time", time)

fun alertEventFromJson(o: JSONObject) = AlertEvent(
    id = o.optString("id"),
    symbol = o.optString("symbol"),
    name = o.optString("name"),
    text = o.optString("text"),
    price = o.optDouble("price", 0.0),
    time = o.optLong("time"),
)
