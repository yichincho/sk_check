package com.yichincho.skcheck.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yichincho.skcheck.MainActivity
import com.yichincho.skcheck.R
import com.yichincho.skcheck.data.Stock

object Notifier {
    private const val CHANNEL = "alerts"
    const val EXTRA_SYMBOL = "symbol"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL, "股價提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "到達你設的價格或漲跌幅時通知"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun alert(context: Context, stock: Stock, text: String, ruleId: String) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SYMBOL, stock.symbol)
        val id = (stock.symbol + ruleId).hashCode()
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${stock.name} ${stock.code}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n點一下查看，再決定要不要動作。"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permission was withdrawn between the check and the call; nothing else to do.
        }
    }
}
