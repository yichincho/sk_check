@file:OptIn(ExperimentalMaterial3Api::class)

package com.yichincho.skcheck.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yichincho.skcheck.BuildConfig
import com.yichincho.skcheck.data.DeepSeek
import com.yichincho.skcheck.data.Gemini
import com.yichincho.skcheck.data.NetException
import com.yichincho.skcheck.data.Nvidia
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.fmtNum
import com.yichincho.skcheck.work.Notifier
import com.yichincho.skcheck.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onAskNotify: () -> Unit) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsStateWithLifecycle()
    var drop by remember { mutableStateOf(fmtNum(settings.defaultDrop)) }
    var rise by remember { mutableStateOf(fmtNum(settings.defaultRise)) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Text("截圖辨識依序用 NVIDIA → Gemini → DeepSeek，有填金鑰的才會用；一家失敗就自動換下一家。金鑰只存在這支手機。",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        KeyPanel(
            title = "① NVIDIA NIM API 金鑰",
            help = "到 build.nvidia.com 申請（通常 nvapi- 開頭）。從即時清單挑選 GLM、NVIDIA Nemotron、Qwen 等識圖候選，並測試圖片輸入；純文字 GLM 不列入。",
            initial = settings.nvidiaKey,
            save = { k -> Store.updateSettings { it.copy(nvidiaKey = k) } },
            test = { k ->
                val top = Nvidia.verify(k)
                Store.updateSettings { s -> s.copy(visionModels = top, visionModel = if (s.visionModel in top) s.visionModel else top.first()) }
                "圖片測試通過，優先模型：${top.first()}；其他模型為清單候選，辨識時自動備援"
            },
        ) {
            ModelChoice(settings.visionModels, settings.visionModel) { m -> Store.updateSettings { it.copy(visionModel = m) } }
        }

        KeyPanel(
            title = "② Gemini 金鑰（備案）",
            help = "到 aistudio.google.com 申請。NVIDIA 讀不出來時改用 Gemini。",
            initial = settings.geminiKey,
            save = { k -> Store.updateSettings { it.copy(geminiKey = k) } },
            test = { k ->
                val models = Gemini.verify(k)
                Store.updateSettings { s -> s.copy(geminiModels = models, geminiModel = if (s.geminiModel in models) s.geminiModel else models.first()) }
                "金鑰可用"
            },
        ) {
            ModelChoice(settings.geminiModels, settings.geminiModel) { m -> Store.updateSettings { it.copy(geminiModel = m) } }
        }

        KeyPanel(
            title = "③ DeepSeek 金鑰（備案）",
            help = "到 platform.deepseek.com 申請。使用即時清單的圖片能力選擇模型，優先 deepseek-flash；自動更新舊實驗版名稱。",
            initial = settings.deepseekKey,
            save = { k -> Store.updateSettings { it.copy(deepseekKey = k) } },
            test = { k ->
                val m = DeepSeek.verify(k)
                Store.updateSettings { it.copy(deepseekModel = m) }
                "圖片測試通過，模型：$m"
            },
        ) {
            Text("模型：${settings.deepseekModel}", fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Panel {
            SectionTitle("通知頻率")
            Text("用當天最高／最低價判斷有沒有到價；條件一直成立時，每隔一段時間再提醒一次。", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            Text("同一條規則多久再提醒", fontSize = 14.sp)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                listOf(1, 2, 3).forEachIndexed { i, h ->
                    SegmentedButton(selected = settings.notifyGapHours == h, onClick = { Store.updateSettings { it.copy(notifyGapHours = h) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text("每 $h 小時") }
                }
            }
            Text("一天最多幾次", fontSize = 14.sp)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                listOf(1, 2, 3, 5).forEachIndexed { i, n ->
                    SegmentedButton(selected = settings.notifyMaxPerDay == n, onClick = { Store.updateSettings { it.copy(notifyMaxPerDay = n) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 4)) { Text("$n 次") }
                }
            }
        }

        Panel {
            SectionTitle("報價更新（Yahoo Finance）")
            Text("只在台股盤中（週一到週五 09:00–13:30）於背景檢查。Yahoo 的台股報價本身約延遲 20 分鐘。", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(5, 10, 15).forEachIndexed { i, min ->
                    SegmentedButton(
                        selected = settings.intervalMin == min,
                        onClick = {
                            Store.updateSettings { it.copy(intervalMin = min) }
                            Scheduler.apply(context, replace = true)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text("每 $min 分") }
                }
            }
            Text(
                if (settings.intervalMin >= 15) "15 分鐘是 Android 背景排程最穩的間隔，最省電。"
                else "短於 15 分鐘時，手機省電模式下可能被延後；建議把本 App 設為「不受電池最佳化限制」。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }, modifier = Modifier.padding(top = 6.dp)) { Text("開啟電池最佳化設定") }
        }

        Panel {
            SectionTitle("新增時的預設警示")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField(drop, { drop = it; it.toDoubleOrNull()?.let { v -> Store.updateSettings { s -> s.copy(defaultDrop = v) } } },
                    "下跌 %", Modifier.weight(1f))
                NumberField(rise, { rise = it; it.toDoubleOrNull()?.let { v -> Store.updateSettings { s -> s.copy(defaultRise = v) } } },
                    "上漲 %", Modifier.weight(1f))
            }
        }

        Panel {
            SectionTitle("通知")
            val can = Notifier.canNotify(context)
            Text(if (can) "已允許通知" else "還沒允許通知，到價時不會跳出提醒", fontSize = 14.sp,
                color = if (can) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!can) Button(onClick = onAskNotify) { Text("允許通知") }
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                    }.onFailure {
                        context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                    }
                }) { Text("通知設定") }
            }
        }

        Text("版本 v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyPanel(
    title: String,
    help: String,
    initial: String,
    save: (String) -> Unit,
    test: suspend (String) -> String,
    extra: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(initial) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    Panel {
        SectionTitle(title)
        Text(help, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        OutlinedTextField(
            key, { key = it.trim() },
            label = { Text("金鑰") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(
                onClick = {
                    testing = true
                    result = ""
                    save(key)
                    scope.launch {
                        try {
                            result = withContext(Dispatchers.IO) { test(key) }
                            ok = true
                        } catch (e: NetException) {
                            ok = false
                            result = e.message ?: "測試失敗"
                        } catch (e: Exception) {
                            ok = false
                            result = "測試失敗：${e.message}"
                        }
                        testing = false
                    }
                },
                enabled = key.isNotBlank() && !testing,
            ) { Text(if (testing) "測試中…" else "儲存並測試") }
            if (initial.isNotBlank()) {
                OutlinedButton(onClick = { key = ""; save(""); result = "已清除" ; ok = true }) { Text("清除") }
            }
        }
        if (result.isNotEmpty()) {
            Text(result, fontSize = 13.sp, color = if (ok) LocalMarketColors.current.down else MaterialTheme.colorScheme.error)
        }
        if (initial.isNotBlank()) extra()
    }
}

@Composable
private fun ModelChoice(models: List<String>, chosen: String, onPick: (String) -> Unit) {
    Text("優先使用的模型", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    models.forEachIndexed { i, m ->
        Row(
            Modifier.fillMaxWidth()
                .selectable(selected = chosen == m, role = Role.RadioButton, onClick = { onPick(m) })
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = chosen == m, onClick = null)
            Text(m, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text("#${i + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
