package io.github.lk024.orario.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.lk024.orario.MainActivity
import io.github.lk024.orario.R
import io.github.lk024.orario.data.Reminder
import io.github.lk024.orario.data.ReminderKind
import io.github.lk024.orario.data.formatMinutes
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tiene programmata una sola sveglia esatta: quella del prossimo promemoria. Quando suona,
 * [ReminderReceiver] mostra le notifiche di quell'orario e programma la successiva.
 */
object ReminderScheduler {

    private const val CHANNEL_BLOCK = "block"
    private const val CHANNEL_LUNCH = "lunch"
    private const val CHANNEL_MANUAL = "manual"
    const val EXTRA_TRIGGER = "trigger"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_BLOCK, "Inizio delle lezioni", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prima lezione del giorno e lezioni dopo un'ora libera"
            },
            NotificationChannel(CHANNEL_LUNCH, "Dopo la pausa pranzo", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_MANUAL, "Promemoria personali", NotificationManager.IMPORTANCE_HIGH),
        ))
    }

    fun reschedule(context: Context, reminders: List<Reminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val next = reminders.firstOrNull { it.triggerAt > now }
        if (next == null) {
            alarms.cancel(alarmIntent(context, 0))
            return
        }
        val millis = next.triggerAt.toMillis()
        val intent = alarmIntent(context, millis)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    fun show(context: Context, reminder: Reminder) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val lesson = reminder.lesson
        val id = notificationId(reminder)
        val channel = when (reminder.kind) {
            ReminderKind.Block -> CHANNEL_BLOCK
            ReminderKind.Lunch -> CHANNEL_LUNCH
            ReminderKind.Manual -> CHANNEL_MANUAL
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${lesson.title} alle ${formatMinutes(reminder.slot.start)}")
            .setContentText(listOfNotNull(lesson.room, lesson.teacher, "tra ${reminder.lead} min").joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.startAt.toMillis())
            .setContentIntent(open)
            .setAutoCancel(true)
            // Sparisce da sola quando la lezione è iniziata
            .setTimeoutAfter(reminder.lead * 60_000L)

        reminder.manual?.let { manual ->
            val delete = Intent(context, ReminderActionReceiver::class.java)
                .putExtra(ReminderActionReceiver.EXTRA_DAY, manual.dayName)
                .putExtra(ReminderActionReceiver.EXTRA_HOUR, manual.hour)
                .putExtra(ReminderActionReceiver.EXTRA_DATE, manual.date)
                .putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION, id)
            builder.addAction(
                0, "Elimina promemoria",
                PendingIntent.getBroadcast(context, id, delete, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun notificationId(reminder: Reminder) =
        ((reminder.date.toEpochDay() * 20 + reminder.slot.hour) * 3 + reminder.kind.ordinal).toInt()

    private fun alarmIntent(context: Context, trigger: Long) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TRIGGER, trigger),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
