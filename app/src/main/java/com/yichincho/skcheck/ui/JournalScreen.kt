@file:OptIn(ExperimentalMaterial3Api::class)

package com.yichincho.skcheck.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yichincho.skcheck.data.AlertEvent
import com.yichincho.skcheck.data.Csv
import com.yichincho.skcheck.data.Side
import com.yichincho.skcheck.data.Snapshot
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.Trade
import com.yichincho.skcheck.data.fmtDateMs
import com.yichincho.skcheck.data.fmtNum
import com.yichincho.skcheck.data.fmtPrice

private sealed class Entry(val time: Long, val key: String) {
    class T(val t: Trade) : Entry(t.createdAt, "t" + t.id)
    class S(val s: Snapshot) : Entry(s.createdAt, "s" + s.id)
    class A(val a: AlertEvent) : Entry(a.time, "a" + a.id)
}

@Composable
fun JournalScreen(onOpenShot: (String) -> Unit, onOpenStock: (String) -> Unit) {
    val context = LocalContext.current
    val trades by Store.trades.collectAsStateWithLifecycle()
    val shots by Store.snapshots.collectAsStateWithLifecycle()
    val events by Store.events.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        msg = try {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(Csv.tradesToCsv(Store.trades.value).toByteArray()) }
            "已匯出 ${Store.trades.value.size} 筆買賣"
        } catch (e: Exception) {
            "匯出失敗：${e.message}"
        }
    }

    val entries = buildList<Entry> {
        if (filter == 0 || filter == 1) trades.forEach { add(Entry.T(it)) }
        if (filter == 0 || filter == 2) shots.forEach { add(Entry.S(it)) }
        if (filter == 0 || filter == 3) events.forEach { add(Entry.A(it)) }
    }.sortedByDescending { it.time }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("交易日誌", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { export.launch("買賣紀錄.csv") }, enabled = trades.isNotEmpty()) { Text("匯出 CSV") }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "買賣 ${trades.size}", "截圖 ${shots.size}", "通知 ${events.size}").forEachIndexed { i, l ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) })
            }
        }
        if (entries.isEmpty()) {
            Text("還沒有紀錄。在個股頁按「記錄買進／賣出」，或從「新增」存截圖。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.key }) { e ->
                when (e) {
                    is Entry.T -> TradeEntry(e.t) { if (Store.stock(e.t.symbol) != null) onOpenStock(e.t.symbol) }
                    is Entry.S -> ShotEntry(e.s) { onOpenShot(e.s.id) }
                    is Entry.A -> AlertEntry(e.a) { if (Store.stock(e.a.symbol) != null) onOpenStock(e.a.symbol) }
                }
            }
        }
    }
}

@Composable
private fun Head(kind: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, title: String, time: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(kind, bg, fg)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(fmtDateMs(time).substringAfter('/'), fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TradeEntry(t: Trade, onClick: () -> Unit) {
    val m = LocalMarketColors.current
    Panel(Modifier.clickable(onClick = onClick)) {
        Head(t.side.label, if (t.side == Side.BUY) m.up else m.down, MaterialTheme.colorScheme.surfaceContainer,
            "${t.name} ${fmtNum(t.qty)} 股 @ ${fmtPrice(t.price)}", t.createdAt)
        if (t.note.isNotBlank()) Text(t.note, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        val extra = listOf(t.date) + t.tags
        Text(extra.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ShotEntry(s: Snapshot, onClick: () -> Unit) {
    var zoom by remember { mutableStateOf(false) }
    if (zoom) ShotViewer(s.imageFile, onDismiss = { zoom = false })
    Panel(Modifier.clickable(onClick = onClick)) {
        Head("截圖", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
            s.title.ifBlank { "${s.rows.size} 檔" }, s.createdAt)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShotImage(s.imageFile, Modifier.size(width = 88.dp, height = 64.dp).clip(RoundedCornerShape(6.dp))
                .clickable { zoom = true }, maxSide = 400, crop = true)
            Column(Modifier.weight(1f)) {
                if (s.note.isNotBlank()) Text(s.note, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (s.rows.isNotEmpty()) Text(s.rows.joinToString(" ") { it.code }, fontSize = 12.sp, fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlertEntry(a: AlertEvent, onClick: () -> Unit) {
    val m = LocalMarketColors.current
    Panel(Modifier.clickable(onClick = onClick)) {
        Head("通知", m.alertBg, m.alertFg, a.name, a.time)
        Text(a.text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
