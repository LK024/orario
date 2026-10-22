package io.github.lk024.orario

import android.app.Application
import io.github.lk024.orario.data.OverridesRepository
import io.github.lk024.orario.data.Reminder
import io.github.lk024.orario.data.RemindersRepository
import io.github.lk024.orario.data.ScheduleRepository
import io.github.lk024.orario.data.SettingsRepository
import io.github.lk024.orario.data.SyncWorker
import io.github.lk024.orario.data.computeReminders
import io.github.lk024.orario.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class OrarioApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val scheduleRepository by lazy { ScheduleRepository(filesDir, BuildConfig.SHEET_CSV_URL) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val overridesRepository by lazy { OverridesRepository(filesDir) }
    val remindersRepository by lazy { RemindersRepository(filesDir) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        ReminderScheduler.createChannels(this)
        // Ogni cambio di orario, supplenze, promemoria o impostazioni riprogramma le sveglie
        appScope.launch {
            loadAll()
            combine(
                scheduleRepository.schedule,
                overridesRepository.overrides,
                remindersRepository.reminders,
                settingsRepository.settings,
            ) { _, _, _, _ -> }.collect { refreshReminders() }
        }
    }

    private suspend fun loadAll() {
        scheduleRepository.load()
        overridesRepository.load()
        remindersRepository.load()
    }

    /** Promemoria da oggi per i prossimi 8 giorni, già ordinati. */
    suspend fun computeUpcomingReminders(): List<Reminder> {
        loadAll()
        val schedule = scheduleRepository.schedule.value ?: return emptyList()
        return computeReminders(
            schedule = schedule,
            overrides = overridesRepository.overrides.value,
            manual = remindersRepository.reminders.value,
            settings = settingsRepository.settings.first().reminderSettings,
            from = LocalDate.now(),
            days = 8,
        )
    }

    suspend fun refreshReminders() = ReminderScheduler.reschedule(this, computeUpcomingReminders())
}
