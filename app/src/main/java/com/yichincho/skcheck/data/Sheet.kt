package com.yichincho.skcheck.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * 每週選股從 Google Sheet 讀進來（FIN_SK 寫進去的），取代拍截圖給 AI 辨識。
 * 網址是 Sheet 裡 Apps Script 部署出來的 …/exec，用 token 保護。
 */
object Sheet {
    /** 從 Sheet 來的紀錄沒有圖片，用這個 model 名稱和 id 前綴分辨。 */
    const val MODEL = "Google Sheet"
    private const val ID_PREFIX = "sheet-"

    data class Week(val week: String, val settle: String, val summary: String, val rows: List<ShotRow>)

    data class Result(val updatedAt: String, val weeks: List<Week>)

    fun snapshotId(week: String) = ID_PREFIX + week

    fun isSheetSnapshot(s: Snapshot) = s.model == MODEL || s.id.startsWith(ID_PREFIX)

    fun fetch(url: String, token: String): Result {
        val picks = get(url, token, "選股")
        val stats = runCatching { get(url, token, "每週績效") }.getOrNull()
        return parse(picks, stats)
    }

    private fun get(url: String, token: String, sheet: String): String {
        val u = url.trim().toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("token", token.trim())
            ?.addQueryParameter("sheet", sheet)
            ?.build() ?: throw NetException("Sheet 網址格式不對（要 …/exec 結尾）")
        val (code, body) = Http.call(Request.Builder().url(u).build(), "Google Sheet")
        if (code !in 200..299) throw NetException("Google Sheet 回應 $code", code)
        val o = runCatching { JSONObject(body) }.getOrNull()
            ?: throw NetException("Google Sheet 回傳的不是資料（網址要用部署後的 …/exec，存取權選「所有人」）")
        if (!o.optBoolean("ok")) throw NetException("Google Sheet：${o.optString("error", "讀取失敗")}")
        return body
    }

    /** [picksJson]：選股分頁；[statsJson]：每週績效分頁（可沒有）。新的週在前面。 */
    fun parse(picksJson: String, statsJson: String? = null): Result {
        val p = JSONObject(picksJson)
        val rows = (p.optJSONArray("rows") ?: org.json.JSONArray()).mapObjects { it }
        val stats = statsJson?.let { s ->
            (JSONObject(s).optJSONArray("rows") ?: org.json.JSONArray()).mapObjects { it }
                .associateBy { it.optString("週別") }
        }.orEmpty()
        val weeks = rows.groupBy { it.optString("週別") }
            .filterKeys { it.isNotBlank() }
            .map { (week, list) ->
                val st = stats[week]
                val summary = st?.let {
                    listOfNotNull(
                        it.optString("平均最高%").takeIf { v -> v.isNotBlank() }?.let { v -> "平均最高 ${signed(v)}%" },
                        it.optString("平均現%").takeIf { v -> v.isNotBlank() }?.let { v -> "現 ${signed(v)}%" },
                    ).joinToString(" · ")
                }.orEmpty()
                Week(week, list.first().optString("結算日"), summary, list.map(::toRow))
            }
            .sortedByDescending { it.week }
        return Result(p.optString("updated_at"), weeks)
    }

    private fun signed(v: String): String = parseNumber(v)?.let { if (it > 0) "+$v" else v } ?: v

    private fun toRow(o: JSONObject): ShotRow {
        val buy = o.optString("買進價")
        val latest = o.optString("現價")
        val high = o.optString("最高價")
        val latestPct = o.optString("現報酬%")
        val highPct = o.optString("最高報酬%")
        val (to, pct) = when {
            latest.isNotBlank() -> latest to latestPct
            high.isNotBlank() -> high to highPct
            else -> buy to ""
        }
        return ShotRow(
            code = o.optString("代號").trim(),
            name = o.optString("名稱").trim(),
            reason = o.optString("理由").trim(),
            eps = o.optString("EPS").trim(),
            from = if (to == buy) "" else buy,
            to = to,
            pct = if (pct.isBlank()) "" else "${signed(pct)}%",
            score = o.optString("品質分").trim(),
        )
    }

    /** 選股當下的買進價：有現價時 from 是買進價，還沒收盤時 to 就是買進價。 */
    fun buyPrice(r: ShotRow): Double? = parseNumber(r.from.ifBlank { r.to })?.takeIf { it > 0 }

    fun baseNote(week: String) = "選股表 $week"

    /** 每個代號最新一次出現的週和那一列。 */
    fun latestByCode(weeks: List<Week>): Map<String, Pair<Week, ShotRow>> {
        val out = linkedMapOf<String, Pair<Week, ShotRow>>()
        weeks.sortedByDescending { it.week }.forEach { w -> w.rows.forEach { r -> out.putIfAbsent(r.code, w to r) } }
        return out
    }

    /** 每個代號在幾週出現過。 */
    fun repeatCounts(weeks: List<Week>): Map<String, Int> =
        weeks.flatMap { w -> w.rows.map { it.code }.distinct() }.groupingBy { it }.eachCount()

    /**
     * 觀察中的股票改用最新一次選股的買進價當漲跌基準。只動基準來自選股表或截圖的；
     * 自己設的（自訂、買進價、昨收）不動。沒有要改就回傳 null。
     */
    fun rebase(s: Stock, latest: Map<String, Pair<Week, ShotRow>>): Stock? {
        if (s.baseIsPrevClose) return null
        if (!(s.baseNote.startsWith("選股表") || s.baseNote == "截圖價")) return null
        val (w, r) = latest[s.code] ?: return null
        val price = buyPrice(r) ?: return null
        val note = baseNote(w.week)
        if (s.basePrice == price && s.baseNote == note) return null
        return s.copy(basePrice = price, baseNote = note, rules = s.rules.map { it.copy(lastFiredDay = "", fireCount = 0) })
    }

    /** 一週變成一筆沒有圖片的紀錄；之前同步過的就更新內容，保留自己寫的說明和已加入的股票。 */
    fun toSnapshot(w: Week, old: Snapshot?): Snapshot {
        val createdAt = runCatching {
            LocalDate.parse(w.week).atTime(9, 0).atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
        return Snapshot(
            id = snapshotId(w.week),
            createdAt = old?.createdAt ?: createdAt,
            imageFile = "",
            title = "選股 ${w.week} ～ ${w.settle}".trimEnd(' ', '～'),
            summary = w.summary,
            note = old?.note.orEmpty(),
            model = MODEL,
            rows = w.rows,
            added = old?.added.orEmpty(),
        )
    }
}
