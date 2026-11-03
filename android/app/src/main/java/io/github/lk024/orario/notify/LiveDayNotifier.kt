package io.github.lk024.orario.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.lk024.orario.MainActivity
import io.github.lk024.orario.R
import io.github.lk024.orario.data.LiveDay
import io.github.lk024.orario.notify.ReminderScheduler.toMillis
import java.time.LocalDateTime

/**
 * Live Update della giornata: notifica "in corso" promossa nella barra di stato, con la pillola come la
 * barra dei menu del Mac e, aprendola, l'elenco delle ore. Cambia solo ai cambi d'ora (nessun
 * aggiornamento al minuto), con una sveglia esatta per ogni cambio.
 */
object LiveDayNotifier {

    private const val CHANNEL = "day"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            // Bassa importanza: niente suono né vibrazione, è un'informazione sempre visibile
            NotificationChannel(CHANNEL, "Giornata in corso", NotificationManager.IMPORTANCE_LOW).apply {
                description = "La lezione in corso nella barra di stato e l'elenco della giornata"
                setShowBadge(false)
            }
        )
    }

    fun update(context: Context, live: LiveDay?, next: LocalDateTime?) {
        val notifications = NotificationManagerCompat.from(context)
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (live == null || !allowed) {
            notifications.cancel(NOTIFICATION_ID)
        } else {
            notifications.notify(NOTIFICATION_ID, build(context, live))
        }
        schedule(context, next)
    }

    private fun build(context: Context, live: LiveDay) =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(live.title)
            .setContentText(live.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(list(live)).setSummaryText(live.text))
            // Live Update: pillola nella barra di stato con il testo breve
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(live.pill)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 1,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    /** L'elenco della giornata, con l'ora in corso in grassetto. */
    private fun list(live: LiveDay): CharSequence {
        val text = SpannableStringBuilder()
        live.lines.forEachIndexed { index, line ->
            if (index > 0) text.append('\n')
            val start = text.length
            text.append(line.text)
            if (line.current) text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    /** Sveglia (non visibile come sveglia) al prossimo cambio d'ora, per ridisegnare o togliere la notifica. */
    private fun schedule(context: Context, next: LocalDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 1, Intent(context, LiveDayReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (next == null) {
            alarms.cancel(intent)
        } else if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toMillis(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toMillis(), intent)
        }
    }
}
