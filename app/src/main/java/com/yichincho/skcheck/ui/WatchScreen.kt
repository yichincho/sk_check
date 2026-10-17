@file:OptIn(ExperimentalMaterial3Api::class)

package com.yichincho.skcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yichincho.skcheck.data.Market
import com.yichincho.skcheck.data.Rules
import com.yichincho.skcheck.data.Stock
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.changePct
import com.yichincho.skcheck.data.fmtPct
import com.yichincho.skcheck.data.fmtPrice
import com.yichincho.skcheck.data.fmtTime
import com.yichincho.skcheck.data.todayTaipei

fun Stock.firedToday(today: String) = rules.any { it.lastFiredDay == today }

@Composable
fun WatchScreen(onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val stocks by Store.stocks.collectAsStateWithLifecycle()
    val trades by Store.trades.collectAsStateWithLifecycle()
    val refreshing by Store.refreshing.collectAsStateWithLifecycle()
    val status by Store.status.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }

    val today = todayTaipei()
    val fired = stocks.filter { it.firedToday(today) }
    val held = stocks.filter { s -> trades.filter { it.symbol == s.symbol }.sumOf { if (it.side.name == "BUY") it.qty else -it.qty } > 0 }
    val shown = when (filter) {
        1 -> fired
        2 -> held
        else -> stocks
    }
    val newest = stocks.maxOfOrNull { it.quoteTime } ?: 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (Market.isOpen()) "盤中 · 每 ${settings.intervalMin} 分檢查" else "已收盤") + " · 報價 ${fmtTime(newest)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("觀察清單", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (refreshing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { Store.refreshInBackground() }) { Icon(Icons.Rounded.Refresh, contentDescription = "立即更新報價") }
            }
        }
        if (status.isNotEmpty()) {
            Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (fired.isNotEmpty()) {
            val m = LocalMarketColors.current
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                    .background(m.alertBg, RoundedCornerShape(12.dp)).clickable { filter = 1 }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = m.alertFg)
                Spacer(Modifier.size(10.dp))
                Text("今天 ${fired.size} 檔已觸發，點一下查看再決定要不要買", fontSize = 14.sp)
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("全部 ${stocks.size}") })
            FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("已觸發 ${fired.size}") })
            FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("已持有 ${held.size}") })
        }

        if (stocks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("還沒有觀察的股票", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("到「新增」輸入代號、匯入 CSV 或貼上截圖。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onAdd) { Text("新增股票") }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.symbol }) { s -> StockRow(s, today, onClick = { onOpen(s.symbol) }) }
        }
    }
}

@Composable
private fun StockRow(s: Stock, today: String, onClick: () -> Unit) {
    val diff = if (s.prevClose > 0) changePct(s.price, s.prevClose) else 0.0
    val m = LocalMarketColors.current
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.size(8.dp))
            Text(s.code, fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (s.price > 0) fmtPrice(s.price) else "—", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val enabled = s.rules.filter { it.enabled }
            Text(
                if (enabled.isEmpty()) "沒有開啟的提醒" else enabled.joinToString("、") { Rules.title(it) },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("今日 ${fmtPct(diff)}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = changeColor(diff))
        }
        if (s.dayHigh > 0 && s.dayLow > 0) {
            Text("今日 高 ${fmtPrice(s.dayHigh)} · 低 ${fmtPrice(s.dayLow)}", fontFamily = Mono, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.firedToday(today)) {
            val r = s.rules.first { it.lastFiredDay == today }
            Pill("已觸發 · ${Rules.title(r)} · 今天通知 ${r.firesToday(today)} 次", m.alertFg, MaterialTheme.colorScheme.surfaceContainer)
        }
        if (s.quoteError.isNotEmpty()) {
            Text(s.quoteError, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
