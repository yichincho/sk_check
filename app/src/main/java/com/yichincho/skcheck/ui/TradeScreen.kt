@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.yichincho.skcheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yichincho.skcheck.data.Side
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.Trade
import com.yichincho.skcheck.data.fmtNum
import com.yichincho.skcheck.data.newId
import com.yichincho.skcheck.data.todayTaipei

private val BUY_TAGS = listOf("跌到目標價", "基本面沒變", "技術面轉強", "財報／EPS 成長", "選股名單", "分批加碼", "通知提醒")
private val SELL_TAGS = listOf("到停利價", "停損", "基本面轉差", "找到更好的標的", "需要資金", "漲幅已滿足")

@Composable
fun TradeScreen(symbol: String, initialSide: Side, onDone: () -> Unit) {
    val stock = Store.stock(symbol)
    var side by rememberSaveable { mutableStateOf(initialSide) }
    var price by rememberSaveable { mutableStateOf(stock?.price?.takeIf { it > 0 }?.let { fmtNum(it) } ?: "") }
    var qty by rememberSaveable { mutableStateOf("1000") }
    var date by rememberSaveable { mutableStateOf(todayTaipei()) }
    var note by rememberSaveable { mutableStateOf("") }
    var moveBase by rememberSaveable { mutableStateOf(true) }
    val tags = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf("") }
    val m = LocalMarketColors.current

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(stock?.let { "${it.name} ${it.code}" } ?: symbol, "記錄${side.label}和理由", onDone)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Side.entries.forEach { sd ->
                    val color = if (sd == Side.BUY) m.up else m.down
                    if (side == sd) {
                        Button(onClick = {}, modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Text(sd.label, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(onClick = { side = sd; tags.clear() }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(sd.label, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(price, { price = it }, "成交價", Modifier.weight(1f))
                NumberField(qty, { qty = it }, "股數", Modifier.weight(1f))
            }
            OutlinedTextField(date, { date = it }, label = { Text("日期（年-月-日）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionTitle("${side.label}理由（可複選）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (side == Side.BUY) BUY_TAGS else SELL_TAGS).forEach { t ->
                    FilterChip(selected = t in tags, onClick = { if (t in tags) tags.remove(t) else tags.add(t) }, label = { Text(t) })
                }
            }
            OutlinedTextField(
                note, { note = it },
                label = { Text("為什麼？寫給未來的自己") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (side == Side.BUY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = moveBase, onCheckedChange = { moveBase = it })
                    Text("把漲跌提醒的基準改成這次買進價", fontSize = 14.sp)
                }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val p = price.toDoubleOrNull()
                    val q = qty.toDoubleOrNull()
                    if (p == null || p <= 0 || q == null || q <= 0) {
                        error = "請填成交價和股數"
                        return@Button
                    }
                    Store.addTrade(
                        Trade(newId(), symbol, stock?.name ?: symbol, side, p, q, date.trim(), tags.toList(), note.trim(), System.currentTimeMillis())
                    )
                    if (side == Side.BUY && moveBase && stock != null) {
                        Store.updateStock(symbol) {
                            it.copy(basePrice = p, baseIsPrevClose = false, baseNote = "買進價", rules = it.rules.map { r -> r.copy(lastFiredDay = "") })
                        }
                    }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("儲存紀錄", fontWeight = FontWeight.Bold) }
        }
    }
}
