@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.yichincho.skcheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yichincho.skcheck.data.Rule
import com.yichincho.skcheck.data.RuleType
import com.yichincho.skcheck.data.Rules
import com.yichincho.skcheck.data.Side
import com.yichincho.skcheck.data.Stock
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.changePct
import com.yichincho.skcheck.data.fmtNum
import com.yichincho.skcheck.data.fmtPct
import com.yichincho.skcheck.data.fmtPrice
import com.yichincho.skcheck.data.fmtTime
import com.yichincho.skcheck.data.newId
import com.yichincho.skcheck.data.todayTaipei

@Composable
fun DetailScreen(symbol: String, onBack: () -> Unit, onTrade: (Side) -> Unit, onOpenShot: (String) -> Unit) {
    val stocks by Store.stocks.collectAsStateWithLifecycle()
    val shots by Store.snapshots.collectAsStateWithLifecycle()
    val trades by Store.trades.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    val s = stocks.firstOrNull { it.symbol == symbol }
    if (s == null) {
        BackBar("找不到這檔", onBack = onBack)
        return
    }
    var addRule by remember { mutableStateOf(false) }
    var editBase by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = todayTaipei()
    val myShots = shots.filter { sh -> sh.added.contains(s.symbol) || sh.rows.any { it.code == s.code } }
    val myTrades = trades.filter { it.symbol == s.symbol }
    val m = LocalMarketColors.current

    Column(Modifier.fillMaxSize()) {
        BackBar("${s.name} ${s.code}", "更新 ${fmtTime(s.quoteTime)} · Yahoo Finance", onBack) {
            IconButton(onClick = { editInfo = true }) { Icon(Icons.Rounded.Edit, contentDescription = "改名稱和備註") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "從清單移除") }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel {
                val vsBase = changePct(s.price, s.base)
                val vsPrev = changePct(s.price, s.prevClose)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (s.price > 0) fmtPrice(s.price) else "—", fontFamily = Mono, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("今日 ${fmtPct(vsPrev)}", fontFamily = Mono, fontWeight = FontWeight.Bold, color = changeColor(vsPrev),
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                if (s.dayHigh > 0 && s.dayLow > 0) {
                    Text("今日 高 ${fmtPrice(s.dayHigh)} · 低 ${fmtPrice(s.dayLow)}（到價以高低點判斷，現價只供參考）",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editBase = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (s.baseIsPrevClose) "基準：昨收 ${fmtPrice(s.prevClose)}" else "基準：${fmtPrice(s.basePrice)}（${s.baseNote}）",
                            fontSize = 14.sp,
                        )
                        Text("相對基準 ${fmtPct(vsBase)}", fontFamily = Mono, fontSize = 13.sp, color = changeColor(vsBase))
                    }
                    Text("改基準", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
                if (s.note.isNotBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(s.note, fontSize = 14.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("警示規則", Modifier.weight(1f))
                TextButton(onClick = { addRule = true }) { Text("＋ 新增規則") }
            }
            if (s.rules.isEmpty()) Text("沒有規則", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.rules.forEach { r ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Rules.title(r), fontWeight = FontWeight.Medium)
                            Text(Rules.describe(r, s).removePrefix(Rules.title(r)).trim().ifEmpty { "固定價格" },
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.lastFiredDay == today) {
                                Text("今天已通知 ${r.firesToday(today)}/${settings.notifyMaxPerDay} 次", fontSize = 12.sp, color = m.alertFg, fontWeight = FontWeight.Bold)
                            } else if (r.enabled && Rules.isHitToday(r, s)) {
                                Text("今天已到價", fontSize = 12.sp, color = m.alertFg, fontWeight = FontWeight.Bold)
                            }
                        }
                        Switch(checked = r.enabled, onCheckedChange = { on ->
                            Store.updateStock(s.symbol) { st -> st.copy(rules = st.rules.map { if (it.id == r.id) it.copy(enabled = on, lastFiredDay = "") else it }) }
                        })
                        IconButton(onClick = {
                            Store.updateStock(s.symbol) { st -> st.copy(rules = st.rules.filterNot { it.id == r.id }) }
                        }) { Icon(Icons.Rounded.Delete, contentDescription = "刪除這條規則") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onTrade(Side.BUY) }, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = m.up, contentColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text("記錄買進", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { onTrade(Side.SELL) }, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = m.down, contentColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text("記錄賣出", fontWeight = FontWeight.Bold)
                }
            }

            SectionTitle("我的買賣紀錄")
            val held = Store.holding(s.symbol)
            if (myTrades.isEmpty()) {
                Text("尚未記錄 · 目前只在觀察", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (held > 0) Text("持有 ${fmtNum(held)} 股 · 買進均價 ${fmtPrice(Store.avgCost(s.symbol))}", fontSize = 14.sp)
                myTrades.forEach { TradeCard(it) }
            }

            SectionTitle("截圖與說明")
            if (myShots.isEmpty()) Text("還沒有含這檔的截圖", color = MaterialTheme.colorScheme.onSurfaceVariant)
            myShots.forEach { sh ->
                Panel(Modifier.clickable { onOpenShot(sh.id) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShotImage(sh.imageFile, Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)), maxSide = 400, crop = true)
                        Column(Modifier.weight(1f)) {
                            val row = sh.rows.firstOrNull { it.code == s.code }
                            Text(com.yichincho.skcheck.data.fmtDateMs(sh.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row != null && row.reason.isNotBlank()) Text(row.reason, fontSize = 13.sp, maxLines = 2)
                            if (sh.note.isNotBlank()) Text(sh.note, fontSize = 14.sp, maxLines = 3)
                        }
                    }
                }
            }
        }
    }

    if (addRule) AddRuleDialog(s, onDismiss = { addRule = false })
    if (editBase) BaseDialog(s, onDismiss = { editBase = false })
    if (editInfo) InfoDialog(s, onDismiss = { editInfo = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("從清單移除 ${s.name}？") },
            text = { Text("規則會一起刪掉；買賣紀錄和截圖會留著。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; Store.removeStock(s.symbol); onBack() }) { Text("移除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
fun TradeCard(t: com.yichincho.skcheck.data.Trade) {
    val m = LocalMarketColors.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(t.side.label, if (t.side == Side.BUY) m.up else m.down, MaterialTheme.colorScheme.surfaceContainer)
            Spacer(Modifier.width(8.dp))
            Text("${fmtNum(t.qty)} 股 @ ${fmtPrice(t.price)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t.date, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (t.tags.isNotEmpty()) Text(t.tags.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (t.note.isNotBlank()) Text(t.note, fontSize = 14.sp)
    }
}

@Composable
private fun AddRuleDialog(s: Stock, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(RuleType.BELOW) }
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增警示規則") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t },
                            label = { Text(if (t == RuleType.DROP || t == RuleType.RISE) "${t.label} %" else t.label) })
                    }
                }
                NumberField(value, { value = it }, if (type == RuleType.DROP || type == RuleType.RISE) "百分比（例如 5）" else "價格",
                    Modifier.fillMaxWidth())
                if (type == RuleType.DROP || type == RuleType.RISE) {
                    Text("相對基準價 ${fmtPrice(s.base)} 計算", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = value.toDoubleOrNull()
                if (v != null && v > 0) {
                    Store.updateStock(s.symbol) { it.copy(rules = it.rules + Rule(newId(), type, v)) }
                    onDismiss()
                }
            }) { Text("加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BaseDialog(s: Stock, onDismiss: () -> Unit) {
    var usePrev by rememberSaveable { mutableStateOf(s.baseIsPrevClose) }
    var price by rememberSaveable { mutableStateOf(fmtNum(s.basePrice)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("漲跌幅的基準價") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("用昨收當基準（每天換）", Modifier.weight(1f))
                    Switch(checked = usePrev, onCheckedChange = { usePrev = it })
                }
                if (!usePrev) {
                    NumberField(price, { price = it }, "固定基準價", Modifier.fillMaxWidth())
                    TextButton(onClick = { price = fmtNum(s.price) }) { Text("用現價 ${fmtPrice(s.price)}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toDoubleOrNull()
                Store.updateStock(s.symbol) {
                    if (usePrev) it.copy(baseIsPrevClose = true, rules = it.rules.map { r -> r.copy(lastFiredDay = "") })
                    else if (p != null && p > 0) it.copy(baseIsPrevClose = false, basePrice = p, baseNote = "自訂",
                        rules = it.rules.map { r -> r.copy(lastFiredDay = "") })
                    else it
                }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun InfoDialog(s: Stock, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(s.name) }
    var note by rememberSaveable { mutableStateOf(s.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名稱與備註") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("顯示名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("備註（為什麼觀察）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.updateStock(s.symbol) { it.copy(name = name.trim().ifBlank { it.name }, note = note.trim()) }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
