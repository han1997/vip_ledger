package com.han1997.vipledger.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.han1997.vipledger.MainActivity
import com.han1997.vipledger.R
import com.han1997.vipledger.core.domain.model.Reminder
import com.han1997.vipledger.core.domain.model.ReminderKind

/** 提醒通知：渠道注册 + 发通知（未授权时静默跳过，应用内提醒卡仍可用）。 */
object ReminderNotifier {

    const val CHANNEL_ID: String = "vipledger.reminders"
    private const val NOTIFICATION_ID: Int = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notify_channel_reminders_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, reminders: List<Reminder>) {
        if (reminders.isEmpty()) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val first = reminders.first()
        val text = buildString {
            append(describe(context, first))
            if (reminders.size > 1) {
                append('\n')
                append(context.getString(R.string.notify_more, reminders.size - 1))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notify_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun describe(context: Context, reminder: Reminder): String = when (reminder.kind) {
        ReminderKind.INSTALLMENT_DUE -> context.getString(
            R.string.notify_installment_due,
            reminder.name,
            reminder.periodNo ?: 0,
            relativeDay(context, reminder.daysUntilDue),
        )

        ReminderKind.INSTALLMENT_OVERDUE -> context.getString(
            R.string.notify_installment_overdue,
            reminder.name,
            reminder.periodNo ?: 0,
            -reminder.daysUntilDue,
        )

        ReminderKind.CARD_BILLING -> context.getString(
            R.string.notify_card_billing,
            reminder.name,
            reminder.dueDate.toString(),
        )

        ReminderKind.CARD_DUE -> context.getString(
            R.string.notify_card_due,
            reminder.name,
            reminder.dueDate.toString(),
            reminder.amount.toDisplayString(),
        )

        ReminderKind.LENDING_DUE -> context.getString(
            R.string.notify_lending_due,
            reminder.name,
            relativeDay(context, reminder.daysUntilDue),
        )

        ReminderKind.LENDING_OVERDUE -> context.getString(
            R.string.notify_lending_overdue,
            reminder.name,
            -reminder.daysUntilDue,
        )
    }

    private fun relativeDay(context: Context, daysUntilDue: Int): String = when {
        daysUntilDue <= 0 -> context.getString(R.string.notify_today)
        daysUntilDue == 1 -> context.getString(R.string.notify_tomorrow)
        else -> context.getString(R.string.notify_in_days, daysUntilDue)
    }
}
