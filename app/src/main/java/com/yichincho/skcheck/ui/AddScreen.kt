@file:OptIn(ExperimentalMaterial3Api::class)

package com.yichincho.skcheck.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yichincho.skcheck.data.Csv
import com.yichincho.skcheck.data.CsvItem
import com.yichincho.skcheck.data.NetException
import com.yichincho.skcheck.data.NewStock
import com.yichincho.skcheck.data.Sheet
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.fmtNum
import com.yichincho.skcheck.data.fmtPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddScreen(onShot: (Uri) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("新增到觀察", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("四種方式都會先給你確認，再加入清單", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            listOf("選股表", "代號", "CSV", "截圖").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 4)) { Text(label, maxLines = 1) }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> SheetTab()
                1 -> CodeTab()
                2 -> CsvTab()
                else -> ShotTab(onShot)
            }
        }
    }
}

@Composable
private fun Report(lines: List<String>) {
    if (lines.isEmpty()) return
    Panel { lines.forEach { Text(it, fontSize = 14.sp) } }
}

@Composable
private fun CodeTab() {
    val settings by Store.settings.collectAsStateWithLifecycle()
    var codes by rememberSaveable { mutableStateOf("") }
    var buy by rememberSaveable { mutableStateOf("") }
    var sell by rememberSaveable { mutableStateOf("") }
    var drop by rememberSaveable { mutableStateOf(fmtNum(settings.defaultDrop)) }
    var rise by rememberSaveable { mutableStateOf(fmtNum(settings.defaultRise)) }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var report by remember { mutableStateOf(listOf<String>()) }

    val list = codes.split(Regex("[\\s,，、]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    OutlinedTextField(
        codes, { codes = it },
        label = { Text("股票代號（可一次多個，用空白或逗號分開）") },
        placeholder = { Text("2330 2317 6488") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("上市會自動補 .TW、上櫃補 .TWO。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Panel {
        Text("警示（空白就不設）", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            NumberField(drop, { drop = it }, "下跌提醒 %", Modifier.weight(1f))
            NumberField(rise, { rise = it }, "上漲提醒 %", Modifier.weight(1f))
        }
        if (list.size <= 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField(buy, { buy = it }, "目標買價 ≤", Modifier.weight(1f))
                NumberField(sell, { sell = it }, "目標賣價 ≥", Modifier.weight(1f))
            }
        }
        Text("漲跌幅以加入時的價格當基準，之後可在個股頁改。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
    OutlinedTextField(note, { note = it }, label = { Text("備註：為什麼觀察（選填）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            val single = list.size <= 1
            val items = list.map {
                NewStock(
                    input = it,
                    buyPrice = if (single) buy.toDoubleOrNull() else null,
                    sellPrice = if (single) sell.toDoubleOrNull() else null,
                    dropPct = drop.toDoubleOrNull(),
                    risePct = rise.toDoubleOrNull(),
                    note = note.trim(),
                )
            }
            Store.scope.launch {
                report = Store.addStocks(items) { busy = it }
                busy = ""
                codes = ""; buy = ""; sell = ""; note = ""
            }
        },
        enabled = list.isNotEmpty() && busy.isEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (busy.isNotEmpty()) busy else "加入 ${list.size} 檔", fontWeight = FontWeight.Bold) }
    Report(report)
}

@Composable
private fun CsvTab() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(listOf<CsvItem>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var report by remember { mutableStateOf(listOf<String>()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            items = Csv.parse(Csv.decode(bytes))
            error = if (items.isEmpty()) "檔案裡沒有找到代號" else ""
            report = emptyList()
        } catch (e: Exception) {
            error = "讀不到這個檔案：${e.message}"
        }
    }
    OutlinedButton(
        onClick = { pick.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) { Text("選擇 CSV 檔", fontWeight = FontWeight.Bold) }
    Text("欄位格式（第一列是標題，只有代號必填）", fontWeight = FontWeight.Bold)
    Text(
        Csv.TEMPLATE + "\n2330,台積電,1000,,5,10,等法說後回檔\n2317,鴻海,190,230,,,",
        fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp),
    )
    Text("UTF-8 或 Big5 都可以；空白的跌幅／漲幅用預設值；已在清單的代號會更新規則。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    if (items.isNotEmpty()) {
        Panel {
            Text("讀到 ${items.size} 檔", fontWeight = FontWeight.Bold)
            items.forEach { c ->
                val parts = listOfNotNull(
                    c.buyPrice?.let { "≤ ${fmtPrice(it)}" }, c.sellPrice?.let { "≥ ${fmtPrice(it)}" },
                    c.dropPct?.let { "跌 ${fmtNum(it)}%" }, c.risePct?.let { "漲 ${fmtNum(it)}%" },
                )
                Text("${c.code} ${c.name}  ${parts.joinToString(" · ")}", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Button(
            onClick = {
                val list = items.map { NewStock(it.code, it.name, it.buyPrice, it.sellPrice, it.dropPct, it.risePct, note = it.note) }
                Store.scope.launch {
                    report = Store.addStocks(list) { busy = it }
                    busy = ""
                    items = emptyList()
                }
            },
            enabled = busy.isEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (busy.isNotEmpty()) busy else "加入 ${items.size} 檔", fontWeight = FontWeight.Bold) }
    }
    Report(report)
}

@Composable
private fun ShotTab(onShot: (Uri) -> Unit) {
    val settings by Store.settings.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onShot(uri) }
    Button(
        onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier.fillMaxWidth().height(96.dp),
    ) { Text("選擇截圖", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    Text("也可以在相簿或其他 App 按「分享」，選「股票提醒」直接送進來。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Panel {
        Text("會做的事", fontWeight = FontWeight.Bold)
        Text("1. 截圖原檔存在手機裡。", fontSize = 14.sp)
        Text("2. AI 讀出代號、名稱、說明、EPS、起始價→目前價（依序用 NVIDIA、Gemini、DeepSeek，失敗就換下一個）。", fontSize = 14.sp)
        Text("3. 你勾選要加入觀察的幾檔，寫下說明，一起存成一筆截圖紀錄。", fontSize = 14.sp)
    }
    if (!com.yichincho.skcheck.data.Vision.hasAnyKey(settings)) {
        Text("還沒設定任何 AI 金鑰：可以先存截圖和說明，辨識要到「設定」貼上 NVIDIA、Gemini 或 DeepSeek 金鑰。", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

/** 從 Google Sheet 讀 FIN_SK 的每週選股：每週存成一筆選股紀錄，勾選的加入觀察。 */
@Composable
private fun SheetTab() {
    val settings by Store.settings.collectAsStateWithLifecycle()
    val stocks by Store.stocks.collectAsStateWithLifecycle()
    var weeks by remember { mutableStateOf(listOf<Sheet.Week>()) }
    var updatedAt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loads by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var report by remember { mutableStateOf(listOf<String>()) }
    /** key = 週別|代號 */
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    val ready = settings.sheetUrl.isNotBlank() && settings.sheetToken.isNotBlank()

    LaunchedEffect(loads, ready) {
        if (!ready) return@LaunchedEffect
        loading = true
        error = ""
        try {
            val r = withContext(Dispatchers.IO) { Sheet.fetch(settings.sheetUrl, settings.sheetToken) }
            Store.syncSheetWeeks(r.weeks)
            weeks = r.weeks
            updatedAt = r.updatedAt
            checked.clear()
            val watching = Store.stocks.value.map { it.code }.toSet()
            r.weeks.firstOrNull()?.rows?.forEach { row -> if (row.code !in watching) checked[r.weeks.first().week + "|" + row.code] = true }
        } catch (e: NetException) {
            error = e.message ?: "讀取失敗"
        } catch (e: Exception) {
            error = "讀取失敗：${e.message}"
        }
        loading = false
    }

    if (!ready) {
        Text("還沒設定 Google Sheet：到「設定」最上面貼上網址（…/exec）和 token。", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { loads++ }, enabled = !loading && busy.isEmpty()) { Text(if (loading) "讀取中…" else "重新讀取") }
        Spacer(Modifier.width(10.dp))
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else if (updatedAt.isNotBlank()) Text("Sheet 更新於 $updatedAt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("每週選股會存成一筆「選股紀錄」（在日誌和個股頁看得到）。勾選的股票加入觀察，用最新那次選股的買進價當漲跌提醒基準。",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)

    val watching = stocks.map { it.code }.toSet()
    val latest = Sheet.latestByCode(weeks)
    val repeats = Sheet.repeatCounts(weeks)
    val dupCodes = repeats.filterValues { it > 1 }.keys
    if (dupCodes.isNotEmpty()) {
        Panel {
            Text("重複選到 ${dupCodes.size} 檔", fontWeight = FontWeight.Bold)
            Text(dupCodes.joinToString("、") { c -> "${latest[c]?.second?.name ?: c} ×${repeats[c]}" }, fontSize = 13.sp)
            Text("漲跌提醒一律用最新那次的買進價當基準；舊的那幾列不能勾。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    weeks.forEach { w ->
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("${w.week} ～ ${w.settle}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (w.summary.isNotBlank()) Text(w.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            w.rows.forEach { r ->
                val key = w.week + "|" + r.code
                val newestWeek = latest[r.code]?.first?.week
                val isLatest = newestWeek == w.week
                val times = repeats[r.code] ?: 1
                Panel(Modifier.padding(top = 6.dp)) {
                    Row {
                        when {
                            !isLatest -> Text("較舊", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
                            r.code in watching -> Text("觀察中", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
                            else -> Checkbox(checked = checked[key] == true, onCheckedChange = { checked[key] = it })
                        }
                        Column(Modifier.weight(1f).padding(top = 4.dp)) {
                            RowBody(r)
                            if (times > 1) {
                                Text(
                                    if (isLatest) "重複 ×$times · 這次最新，漲跌以這次買進價 ${Sheet.buyPrice(r)?.let { fmtPrice(it) } ?: "—"} 為準"
                                    else "重複 ×$times · 以 $newestWeek 那次為準",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val chosen = latest.values.filter { (w, r) -> checked[w.week + "|" + r.code] == true && r.code !in watching }
    if (weeks.isNotEmpty()) {
        Button(
            onClick = {
                Store.scope.launch {
                    report = Store.addStocks(chosen.map { (w, r) ->
                        NewStock(input = r.code, name = r.name, basePrice = Sheet.buyPrice(r), baseNote = Sheet.baseNote(w.week), note = r.reason)
                    }) { busy = it }
                    chosen.groupBy({ it.first.week }, { it.second.code }).forEach { (week, codes) ->
                        val syms = Store.stocks.value.filter { it.code in codes }.map { it.symbol }
                        Store.updateSnapshot(Sheet.snapshotId(week)) { it.copy(added = (it.added + syms).distinct()) }
                    }
                    checked.clear()
                    busy = ""
                }
            },
            enabled = chosen.isNotEmpty() && busy.isEmpty() && !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (busy.isNotEmpty()) busy else "加入 ${chosen.size} 檔", fontWeight = FontWeight.Bold) }
    }
    Report(report)
}
