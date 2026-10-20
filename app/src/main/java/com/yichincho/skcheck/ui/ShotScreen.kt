@file:OptIn(ExperimentalMaterial3Api::class)

package com.yichincho.skcheck.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.yichincho.skcheck.data.Images
import com.yichincho.skcheck.data.NetException
import com.yichincho.skcheck.data.NewStock
import com.yichincho.skcheck.data.Vision
import com.yichincho.skcheck.data.ShotRow
import com.yichincho.skcheck.data.Snapshot
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.fmtDateMs
import com.yichincho.skcheck.data.newId
import com.yichincho.skcheck.data.parseNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Pick(val row: ShotRow, val on: Boolean)

/** A newly picked or shared screenshot: copy it in, let the AI read it, then pick stocks and write a note. */
@Composable
fun NewShotScreen(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsStateWithLifecycle()
    val id = rememberSaveable(uri) { newId() }
    var fileName by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val picks = remember { mutableStateListOf<Pick>() }
    var editing by remember { mutableIntStateOf(-1) }
    var runs by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        stage = "複製截圖…"
        try {
            val f = withContext(Dispatchers.IO) { Images.copyIn(context, uri, id) }
            fileName = f.name
            stage = ""
            if (Vision.hasAnyKey(settings)) runs++
        } catch (e: Exception) {
            stage = ""
            error = "這張圖打不開：${e.message}"
        }
    }
    LaunchedEffect(runs) {
        if (runs == 0 || fileName.isEmpty()) return@LaunchedEffect
        stage = "AI 讀圖中…（約 10–60 秒）"
        error = ""
        try {
            val s = Store.settings.value
            val result = withContext(Dispatchers.IO) {
                Vision.readShot(s, Images.toJpegBase64(Store.shotFile(fileName))) { who -> stage = "$who 讀圖中…（約 10–60 秒）" }
            }
            title = result.title
            summary = result.summary
            model = result.model
            picks.clear()
            result.rows.forEach { picks.add(Pick(it, true)) }
        } catch (e: NetException) {
            error = e.message ?: "讀圖失敗"
        } catch (e: Exception) {
            error = "讀圖失敗：${e.message}"
        }
        stage = ""
    }

    fun leave() {
        if (fileName.isNotEmpty() && Store.snapshots.value.none { it.imageFile == fileName }) Store.shotFile(fileName).delete()
        onDone()
    }

    fun save(addStocks: Boolean) {
        val chosen = picks.filter { it.on }.map { it.row }
        val snap = Snapshot(id, System.currentTimeMillis(), fileName, title, summary, note.trim(), model, picks.map { it.row }, emptyList())
        Store.addSnapshot(snap)
        if (!addStocks || chosen.isEmpty()) {
            onDone()
            return
        }
        Store.scope.launch {
            val items = chosen.map { r ->
                NewStock(input = r.code, name = r.name, basePrice = r.toPrice, baseNote = "截圖價", note = r.reason)
            }
            Store.addStocks(items) { saving = it }
            val codes = chosen.map { it.code }.toSet()
            val symbols = Store.stocks.value.filter { it.code in codes }.map { it.symbol }
            Store.updateSnapshot(id) { it.copy(added = symbols) }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar("截圖", if (model.isNotEmpty()) "辨識模型：$model" else "", ::leave)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (fileName.isNotEmpty()) {
                Panel {
                    ShotImage(fileName, Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { zoom = true })
                    Text("點圖可放大", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (stage.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stage)
                }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            if (!Vision.hasAnyKey(settings)) {
                Text("還沒設定 NVIDIA／Gemini／DeepSeek 金鑰，所以不辨識；可以直接寫說明、存截圖。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (stage.isEmpty() && fileName.isNotEmpty()) {
                OutlinedButton(onClick = { runs++ }) { Text(if (picks.isEmpty()) "辨識這張圖" else "重新辨識") }
            }
            if (title.isNotBlank() || summary.isNotBlank()) {
                Panel {
                    if (title.isNotBlank()) Text(title, fontWeight = FontWeight.Bold)
                    if (summary.isNotBlank()) Text(summary, fontSize = 14.sp)
                }
            }
            if (picks.isNotEmpty()) {
                SectionTitle("讀到 ${picks.size} 檔 · 勾選要加入觀察的")
                Text("點一列可以修正代號或名稱。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                picks.forEachIndexed { i, p ->
                    Panel(Modifier.clickable { editing = i }) {
                        Row {
                            Checkbox(checked = p.on, onCheckedChange = { picks[i] = p.copy(on = it) })
                            RowBody(p.row, Modifier.weight(1f).padding(top = 4.dp))
                        }
                    }
                }
            }
            OutlinedTextField(
                note, { note = it },
                label = { Text("我的說明") },
                placeholder = { Text("為什麼存這張？當下怎麼想？") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("截圖原檔、辨識結果和說明會一起存成一筆紀錄，勾選的每一檔個股頁都看得到。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (picks.any { it.on }) {
                Text("加入時用截圖上的目前價當漲跌提醒的基準。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { save(false) }, enabled = fileName.isNotEmpty() && saving.isEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp)) { Text("只存截圖") }
                val n = picks.count { it.on }
                Button(onClick = { save(true) }, enabled = fileName.isNotEmpty() && n > 0 && saving.isEmpty() && stage.isEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(if (saving.isNotEmpty()) saving else "加入 $n 檔", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (zoom) ShotViewer(fileName, onDismiss = { zoom = false })

    if (editing in picks.indices) {
        val p = picks[editing]
        var code by remember(editing) { mutableStateOf(p.row.code) }
        var name by remember(editing) { mutableStateOf(p.row.name) }
        var price by remember(editing) { mutableStateOf(p.row.to) }
        var score by remember(editing) { mutableStateOf(p.row.score) }
        AlertDialog(
            onDismissRequest = { editing = -1 },
            title = { Text("修正這一列") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(code, { code = it }, label = { Text("代號") }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("名稱") }, singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("目前價（基準）") }, singleLine = true)
                    OutlinedTextField(score, { score = it }, label = { Text("品質分數") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    picks[editing] = p.copy(row = p.row.copy(code = code.trim(), name = name.trim(), to = price.trim(), score = score.trim()))
                    editing = -1
                }) { Text("好") }
            },
            dismissButton = {
                TextButton(onClick = { picks.removeAt(editing); editing = -1 }) { Text("刪掉這列") }
            },
        )
    }
}

@Composable
fun RowBody(r: ShotRow, modifier: Modifier = Modifier, full: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.code, fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(r.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            ScorePill(r.score)
        }
        if (r.reason.isNotBlank()) Text(r.reason, fontSize = 13.sp, maxLines = if (full) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        if (r.eps.isNotBlank()) Text(r.eps, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r.to.isNotBlank()) {
            val pct = parseNumber(r.pct) ?: 0.0
            val line = (if (r.from.isNotBlank()) "${r.from} → ${r.to}" else r.to) + if (r.pct.isNotBlank()) "（${r.pct}）" else ""
            Text(line, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = changeColor(pct))
        }
    }
}

/** A saved screenshot: picture, the rows read from it, and the note (editable). */
@Composable
fun SavedShotScreen(id: String, onBack: () -> Unit, onOpenStock: (String) -> Unit) {
    val shots by Store.snapshots.collectAsStateWithLifecycle()
    val stocks by Store.stocks.collectAsStateWithLifecycle()
    val s = shots.firstOrNull { it.id == id }
    if (s == null) {
        BackBar("找不到這張截圖", onBack = onBack)
        return
    }
    var note by rememberSaveable(id) { mutableStateOf(s.note) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar("截圖紀錄", fmtDateMs(s.createdAt), onBack) {
            IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, contentDescription = "刪除這張截圖") }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel {
                ShotImage(s.imageFile, Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { zoom = true })
                Text("點圖可放大", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            OutlinedTextField(note, { note = it }, label = { Text("我的說明") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            if (note.trim() != s.note) {
                Button(onClick = { Store.updateSnapshot(id) { it.copy(note = note.trim()) } }) { Text("儲存說明") }
            }
            if (s.title.isNotBlank() || s.summary.isNotBlank()) {
                Panel {
                    if (s.title.isNotBlank()) Text(s.title, fontWeight = FontWeight.Bold)
                    if (s.summary.isNotBlank()) Text(s.summary, fontSize = 14.sp)
                }
            }
            if (s.rows.isNotEmpty()) SectionTitle("截圖上的 ${s.rows.size} 檔")
            s.rows.forEach { r ->
                val onList = stocks.firstOrNull { it.code == r.code }
                Panel(if (onList != null) Modifier.clickable { onOpenStock(onList.symbol) } else Modifier) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowBody(r, Modifier.weight(1f), full = true)
                        if (onList == null) {
                            TextButton(enabled = busy.isEmpty(), onClick = {
                                Store.scope.launch {
                                    Store.addStocks(listOf(NewStock(r.code, r.name, basePrice = r.toPrice, baseNote = "截圖價", note = r.reason))) { busy = it }
                                    val sym = Store.stocks.value.firstOrNull { it.code == r.code }?.symbol
                                    if (sym != null) Store.updateSnapshot(id) { it.copy(added = (it.added + sym).distinct()) }
                                    busy = ""
                                }
                            }) { Text("加入觀察") }
                        } else {
                            Text("觀察中", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (busy.isNotEmpty()) Text(busy, fontSize = 13.sp)
            if (s.model.isNotBlank()) Text("辨識模型：${s.model}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (zoom) ShotViewer(s.imageFile, onDismiss = { zoom = false })
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("刪除這張截圖？") },
            text = { Text("截圖和說明都會刪掉，觀察清單裡的股票不受影響。") },
            confirmButton = { TextButton(onClick = { confirm = false; Store.deleteSnapshot(id); onBack() }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
}
