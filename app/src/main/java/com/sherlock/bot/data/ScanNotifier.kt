package com.sherlock.bot.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sherlock.bot.MainActivity

/**
 * Notifies when a username scan finishes while the UI is in the background.
 */
object ScanNotifier {
    const val CHANNEL_ID = "scan_complete"
    private const val NOTIFICATION_ID = 7101

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Завершение скана",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления, когда скан ника закончился в фоне"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyScanFinished(
        context: Context,
        username: String,
        found: Int,
        uncertain: Int,
        cancelled: Boolean,
    ) {
        ensureChannel(context)
        val title = if (cancelled) {
            "Скан @$username остановлен"
        } else {
            "Скан @$username готов"
        }
        val text = buildString {
            append("найдено: $found")
            if (uncertain > 0) append(" · неуверенно: $uncertain")
        }
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
