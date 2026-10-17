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
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(settings.nvidiaKey) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var drop by remember { mutableStateOf(fmtNum(settings.defaultDrop)) }
    var rise by remember { mutableStateOf(fmtNum(settings.defaultRise)) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Panel {
            SectionTitle("NVIDIA API 金鑰（截圖辨識用）")
            Text("到 build.nvidia.com 申請，貼上 nvapi- 開頭的金鑰。只存在這支手機。", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            OutlinedTextField(
                key, { key = it.trim() },
                label = { Text("金鑰") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    testing = true
                    result = ""
                    Store.updateSettings { it.copy(nvidiaKey = key) }
                    scope.launch {
                        try {
                            val top = withContext(Dispatchers.IO) { Nvidia.verify(key) }
                            Store.updateSettings { s -> s.copy(visionModels = top, visionModel = if (s.visionModel in top) s.visionModel else top.first()) }
                            ok = true
                            result = "金鑰可用，已抓到最新的識圖模型"
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
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (testing) "測試中…" else "儲存並測試金鑰") }
            if (result.isNotEmpty()) {
                Text(result, fontSize = 13.sp, color = if (ok) LocalMarketColors.current.down else MaterialTheme.colorScheme.error)
            }
        }

        Panel {
            SectionTitle("識圖模型 · 熱門 Top 5")
            Text("測試金鑰時從 NVIDIA 抓最新清單、挑出能看圖的模型。第一個失敗會自動換下一個。", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            settings.visionModels.forEachIndexed { i, m ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = settings.visionModel == m, role = Role.RadioButton,
                            onClick = { Store.updateSettings { it.copy(visionModel = m) } })
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.visionModel == m, onClick = null)
                    Text(m, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("#${i + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
