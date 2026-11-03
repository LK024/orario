package io.github.lk024.orario

import android.app.Application
import io.github.lk024.orario.data.OverridesRepository
import io.github.lk024.orario.data.Reminder
import io.github.lk024.orario.data.RemindersRepository
import io.github.lk024.orario.data.ScheduleRepository
import io.github.lk024.orario.data.SettingsRepository
import io.github.lk024.orario.data.SubstitutionsApi
import io.github.lk024.orario.data.SyncWorker
import io.github.lk024.orario.data.computeReminders
import io.github.lk024.orario.data.liveDay
import io.github.lk024.orario.data.nextLiveDayChange
import io.github.lk024.orario.notify.LiveDayNotifier
import io.github.lk024.orario.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class OrarioApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val scheduleRepository by lazy { ScheduleRepository(filesDir, BuildConfig.SHEET_CSV_URL) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val overridesRepository by lazy {
        OverridesRepository(filesDir, SubstitutionsApi(BuildConfig.SYNC_URL, BuildConfig.SYNC_TOKEN))
    }
    val remindersRepository by lazy { RemindersRepository(filesDir) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        ReminderScheduler.createChannels(this)
        LiveDayNotifier.createChannel(this)
        // Ogni cambio di orario, supplenze, promemoria o impostazioni riprogramma le sveglie
        appScope.launch {
            loadAll()
            combine(
                scheduleRepository.schedule,
                overridesRepository.overrides,
                remindersRepository.reminders,
                settingsRepository.settings,
            ) { _, _, _, _ -> }.collect {
                refreshReminders()
                refreshLiveDay()
            }
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

    /** Mostra, aggiorna o toglie il Live Update della giornata e programma il prossimo cambio. */
    suspend fun refreshLiveDay() {
        loadAll()
        val settings = settingsRepository.settings.first()
        val schedule = scheduleRepository.schedule.value
        if (!settings.liveDay || schedule == null) {
            LiveDayNotifier.update(this, live = null, next = null)
            return
        }
        val overrides = overridesRepository.overrides.value
        val now = LocalDateTime.now()
        LiveDayNotifier.update(
            this,
            live = liveDay(schedule, overrides, settings.reminderSettings, now),
            next = nextLiveDayChange(schedule, overrides, settings.reminderSettings, now),
        )
    }
}
