package io.github.lk024.orario.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.lk024.orario.OrarioApplication
import io.github.lk024.orario.data.ManualReminder
import io.github.lk024.orario.notify.ReminderScheduler.toMillis
import kotlinx.coroutines.launch

private fun BroadcastReceiver.runAsync(context: Context, block: suspend (OrarioApplication) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext as OrarioApplication
    app.appScope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

/** Sveglia del prossimo promemoria: mostra le notifiche di quell'orario e programma la successiva. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { app ->
        val trigger = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER, 0)
        val reminders = app.computeUpcomingReminders()
        reminders.filter { it.triggerAt.toMillis() == trigger }.forEach { ReminderScheduler.show(context, it) }
        ReminderScheduler.reschedule(context, reminders)
    }
}

/** Tasto "Elimina promemoria" della notifica di un promemoria manuale. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { app ->
        val dayName = intent.getStringExtra(EXTRA_DAY) ?: return@runAsync
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        app.remindersRepository.remove(ManualReminder(dayName, hour, lead = 0, date = intent.getStringExtra(EXTRA_DATE)))
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        const val EXTRA_DAY = "day"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_DATE = "date"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** Dopo un riavvio, un aggiornamento dell'app o un cambio d'ora le sveglie vanno riprogrammate. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { app -> app.refreshReminders() }
}
