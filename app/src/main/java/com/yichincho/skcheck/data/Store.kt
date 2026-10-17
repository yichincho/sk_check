package com.yichincho.skcheck.data

import android.content.Context
import com.yichincho.skcheck.work.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What a new stock starts with. Prices left null are not turned into rules. */
data class NewStock(
    val input: String,
    val name: String = "",
    val buyPrice: Double? = null,
    val sellPrice: Double? = null,
    val dropPct: Double? = null,
    val risePct: Double? = null,
    val basePrice: Double? = null,
    val baseNote: String = "",
    val note: String = "",
)

/** App-wide state, kept as small JSON files in the app's private storage. */
object Store {
    private lateinit var appContext: Context
    private lateinit var dir: File
    private lateinit var settingsStore: SettingsStore
    private var ready = false
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val fileLock = Any()

    val settings = MutableStateFlow(Settings())
    val stocks = MutableStateFlow<List<Stock>>(emptyList())
    val snapshots = MutableStateFlow<List<Snapshot>>(emptyList())
    val trades = MutableStateFlow<List<Trade>>(emptyList())
    val events = MutableStateFlow<List<AlertEvent>>(emptyList())

    /** Short status line shown at the top of the watchlist ("更新中…", errors). */
    val status = MutableStateFlow("")
    val refreshing = MutableStateFlow(false)
    val lastRefresh = MutableStateFlow(0L)

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        appContext = context.applicationContext
        dir = appContext.filesDir
        File(dir, "shots").mkdirs()
        settingsStore = SettingsStore(appContext)
        settings.value = settingsStore.load()
        stocks.value = read("stocks.json").mapObjects(::stockFromJson)
        snapshots.value = read("snapshots.json").mapObjects(::snapshotFromJson)
        trades.value = read("trades.json").mapObjects(::tradeFromJson)
        events.value = read("events.json").mapObjects(::alertEventFromJson)
        ready = true
    }

    // ---------- files ----------

    private fun read(name: String): JSONArray {
        val f = File(dir, name)
        if (!f.exists()) return JSONArray()
        return try {
            JSONArray(f.readText())
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun write(name: String, arr: JSONArray) = synchronized(fileLock) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(dir, name))
    }

    private fun <T> arr(list: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> list.forEach { a.put(f(it)) } }

    private fun saveStocks() = write("stocks.json", arr(stocks.value) { it.toJson() })
    private fun saveSnapshots() = write("snapshots.json", arr(snapshots.value) { it.toJson() })
    private fun saveTrades() = write("trades.json", arr(trades.value) { it.toJson() })
    private fun saveEvents() = write("events.json", arr(events.value) { it.toJson() })

    fun shotFile(name: String) = File(File(dir, "shots"), name)

    // ---------- settings ----------

    fun updateSettings(f: (Settings) -> Settings) {
        synchronized(this) { settings.value = f(settings.value) }
        settingsStore.save(settings.value)
    }

    // ---------- stocks ----------

    fun stock(symbol: String) = stocks.value.firstOrNull { it.symbol == symbol }

    fun updateStock(symbol: String, f: (Stock) -> Stock) {
        synchronized(this) { stocks.value = stocks.value.map { if (it.symbol == symbol) f(it) else it } }
        saveStocks()
    }

    fun removeStock(symbol: String) {
        synchronized(this) { stocks.value = stocks.value.filterNot { it.symbol == symbol } }
        saveStocks()
    }

    private fun rulesFor(n: NewStock): List<Rule> {
        val s = settings.value
        val out = mutableListOf<Rule>()
        n.buyPrice?.let { out.add(Rule(newId(), RuleType.BELOW, it)) }
        n.sellPrice?.let { out.add(Rule(newId(), RuleType.ABOVE, it)) }
        out.add(Rule(newId(), RuleType.DROP, n.dropPct ?: s.defaultDrop))
        out.add(Rule(newId(), RuleType.RISE, n.risePct ?: s.defaultRise))
        return out
    }

    /**
     * Looks each code up on Yahoo and adds it. A code already on the list gets its price rules (and note)
     * updated instead of a second copy. Returns one line per code saying what happened.
     */
    suspend fun addStocks(items: List<NewStock>, onProgress: (String) -> Unit = {}): List<String> {
        val report = mutableListOf<String>()
        items.forEachIndexed { i, n ->
            onProgress("查詢 ${n.input}（${i + 1}/${items.size}）…")
            try {
                val q = Yahoo.resolve(n.input)
                val name = n.name.ifBlank { q.name }
                val existing = stock(q.symbol)
                if (existing != null) {
                    updateStock(q.symbol) { old ->
                        val keep = old.rules.filterNot { r ->
                            (r.type == RuleType.BELOW && n.buyPrice != null) || (r.type == RuleType.ABOVE && n.sellPrice != null) ||
                                (r.type == RuleType.DROP && n.dropPct != null) || (r.type == RuleType.RISE && n.risePct != null)
                        }
                        val fresh = buildList {
                            n.buyPrice?.let { add(Rule(newId(), RuleType.BELOW, it)) }
                            n.sellPrice?.let { add(Rule(newId(), RuleType.ABOVE, it)) }
                            n.dropPct?.let { add(Rule(newId(), RuleType.DROP, it)) }
                            n.risePct?.let { add(Rule(newId(), RuleType.RISE, it)) }
                        }
                        old.copy(
                            name = if (n.name.isNotBlank()) n.name else old.name,
                            note = if (n.note.isNotBlank()) n.note else old.note,
                            rules = keep + fresh,
                            price = q.price, prevClose = q.prevClose, quoteTime = q.time, quoteError = "",
                            dayHigh = q.dayHigh, dayLow = q.dayLow,
                        )
                    }
                    report.add("${q.symbol} $name：已在清單，更新規則")
                } else {
                    val base = n.basePrice?.takeIf { it > 0 } ?: q.price
                    val stock = Stock(
                        symbol = q.symbol,
                        name = name,
                        basePrice = base,
                        baseIsPrevClose = false,
                        baseNote = n.baseNote.ifBlank { "加入時" },
                        note = n.note,
                        addedAt = System.currentTimeMillis(),
                        rules = rulesFor(n),
                        price = q.price, prevClose = q.prevClose, quoteTime = q.time,
                        dayHigh = q.dayHigh, dayLow = q.dayLow,
                    )
                    synchronized(this) { stocks.value = stocks.value + stock }
                    saveStocks()
                    report.add("${q.symbol} $name：已加入")
                }
            } catch (e: NetException) {
                report.add("${n.input}：${e.message}")
            }
            if (i < items.size - 1) delay(400)
        }
        return report
    }

    // ---------- quotes ----------

    /** Fetches every stock's quote, then sends any alert that is due. Returns a short summary. */
    suspend fun refreshAll(notify: Boolean = true): String = refreshLock.withLock {
        if (!ready) return ""
        refreshing.value = true
        var failed = 0
        try {
            for (s in stocks.value) {
                try {
                    val q = Yahoo.fetch(s.symbol)
                    updateStock(s.symbol) {
                        it.copy(
                            price = q.price, prevClose = q.prevClose, quoteTime = q.time, quoteError = "",
                            dayHigh = q.dayHigh, dayLow = q.dayLow,
                            basePrice = if (it.basePrice > 0) it.basePrice else q.price,
                        )
                    }
                } catch (e: NetException) {
                    failed++
                    updateStock(s.symbol) { it.copy(quoteError = e.message ?: "更新失敗") }
                    if (e.httpCode == 429) break
                }
                delay(300)
            }
            if (notify) checkAlerts()
            lastRefresh.value = System.currentTimeMillis()
            val msg = if (failed > 0) "$failed 檔沒更新到，稍後會再試" else ""
            status.value = msg
            return msg
        } finally {
            refreshing.value = false
        }
    }

    fun refreshInBackground() {
        scope.launch { refreshAll(true) }
    }

    private fun checkAlerts() {
        val today = todayTaipei()
        val now = System.currentTimeMillis()
        val set = settings.value
        val limits = Rules.Limits(set.notifyGapHours, set.notifyMaxPerDay)
        var any = false
        for (s in stocks.value) {
            val due = Rules.toFire(s, today, now, limits)
            if (due.isEmpty()) continue
            any = true
            val updated = due.associate { it.id to Rules.fired(it, today, now) }
            for (r in due) {
                val n = updated.getValue(r.id).fireCount
                val text = Rules.firedText(r, s)
                val ev = AlertEvent(newId(), s.symbol, s.name, text, s.price, now)
                synchronized(this) { events.value = (listOf(ev) + events.value).take(300) }
                Notifier.alert(appContext, s, "$text（今天第 $n/${limits.maxPerDay} 次）", r.id)
            }
            updateStock(s.symbol) { st -> st.copy(rules = st.rules.map { updated[it.id] ?: it }) }
        }
        if (any) saveEvents()
    }

    // ---------- snapshots, trades ----------

    fun addSnapshot(s: Snapshot) {
        synchronized(this) { snapshots.value = listOf(s) + snapshots.value }
        saveSnapshots()
    }

    fun updateSnapshot(id: String, f: (Snapshot) -> Snapshot) {
        synchronized(this) { snapshots.value = snapshots.value.map { if (it.id == id) f(it) else it } }
        saveSnapshots()
    }

    fun deleteSnapshot(id: String) {
        val s = snapshots.value.firstOrNull { it.id == id } ?: return
        shotFile(s.imageFile).delete()
        synchronized(this) { snapshots.value = snapshots.value.filterNot { it.id == id } }
        saveSnapshots()
    }

    fun addTrade(t: Trade) {
        synchronized(this) { trades.value = listOf(t) + trades.value }
        saveTrades()
    }

    fun deleteTrade(id: String) {
        synchronized(this) { trades.value = trades.value.filterNot { it.id == id } }
        saveTrades()
    }

    /** Shares held of [symbol] according to the recorded trades. */
    fun holding(symbol: String): Double =
        trades.value.filter { it.symbol == symbol }.sumOf { if (it.side == Side.BUY) it.qty else -it.qty }

    /** Average cost of what is still held, from buys only (simple average). */
    fun avgCost(symbol: String): Double {
        val buys = trades.value.filter { it.symbol == symbol && it.side == Side.BUY }
        val qty = buys.sumOf { it.qty }
        return if (qty > 0) buys.sumOf { it.price * it.qty } / qty else 0.0
    }
}
