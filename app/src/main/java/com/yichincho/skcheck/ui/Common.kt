package com.yichincho.skcheck.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yichincho.skcheck.data.Images
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.data.parseNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Mono = FontFamily.Monospace

@Composable
fun changeColor(diff: Double): Color {
    val m = LocalMarketColors.current
    return when {
        diff > 0 -> m.up
        diff < 0 -> m.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 品質分數 pill: pink at 70 and above, yellow below; nothing when the screenshot had none. */
@Composable
fun ScorePill(score: String) {
    if (score.isBlank()) return
    val m = LocalMarketColors.current
    val high = (parseNumber(score) ?: 0.0) >= 70
    Pill("品質 $score", if (high) m.alertBg else m.warnBg, if (high) m.alertFg else m.warnFg)
}

@Composable
fun BackBar(title: String, subtitle: String = "", onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Shows a saved screenshot, decoded off the main thread. */
@Composable
fun ShotImage(fileName: String, modifier: Modifier = Modifier, maxSide: Int = 1600, crop: Boolean = false) {
    val bmp by produceState<ImageBitmap?>(null, fileName) {
        value = withContext(Dispatchers.IO) { Images.decode(Store.shotFile(fileName), maxSide)?.asImageBitmap() }
    }
    val b = bmp
    if (b == null) {
        Box(modifier.heightIn(min = 60.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
    } else {
        Image(
            bitmap = b,
            contentDescription = "截圖",
            contentScale = if (crop) ContentScale.Crop else ContentScale.FillWidth,
            modifier = modifier,
        )
    }
}

/** Full-screen screenshot: pinch to zoom, drag to pan, double-tap to toggle 1x / 2.5x, tap or back to close. */
@Composable
fun ShotViewer(fileName: String, onDismiss: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, fileName) {
        value = withContext(Dispatchers.IO) { Images.decode(Store.shotFile(fileName), 2400)?.asImageBitmap() }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val b = bmp
            if (b == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                Image(
                    bitmap = b,
                    contentDescription = "截圖",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onDismiss() },
                                onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                offset = if (scale == 1f) Offset.Zero
                                else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = Color.White)
            }
        }
    }
}
