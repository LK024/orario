package io.github.lk024.orario.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lk024.orario.OrarioApplication
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.ManualReminder
import io.github.lk024.orario.data.Override
import io.github.lk024.orario.data.OverrideKey
import io.github.lk024.orario.data.Settings
import io.github.lk024.orario.data.SyncResult
import io.github.lk024.orario.data.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Messaggio per lo snackbar, con un'azione facoltativa (es. "Annulla"). */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val schedules = (application as OrarioApplication).scheduleRepository
    private val settingsRepository = (application as OrarioApplication).settingsRepository
    private val overridesRepository = (application as OrarioApplication).overridesRepository
    private val remindersRepository = (application as OrarioApplication).remindersRepository

    val schedule = schedules.schedule
    val lastSync = schedules.lastSync
    val overrides = overridesRepository.overrides
    val manualReminders = remindersRepository.reminders
    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Lo splash resta finché non ci sono sia l'orario in cache sia le impostazioni: niente sfarfallii. */
    val ready: StateFlow<Boolean> =
        combine(schedules.loaded, overridesRepository.loaded, remindersRepository.loaded, settings) { schedule, overrides, reminders, settings ->
            schedule && overrides && reminders && settings != null
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    /** Solo per l'aggiornamento chiesto dall'utente (pull-to-refresh o pulsante). */
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var backgroundSync: Job? = null

    init {
        viewModelScope.launch { schedules.load() }
        viewModelScope.launch { overridesRepository.load() }
        viewModelScope.launch { remindersRepository.load() }
    }

    /** All'apertura aggiorna in silenzio, al massimo ogni 15 minuti. */
    fun onForeground() {
        if (backgroundSync?.isActive == true) return
        backgroundSync = viewModelScope.launch {
            schedules.load()
            val last = lastSync.value
            if (last != null && System.currentTimeMillis() - last < FOREGROUND_SYNC_INTERVAL) return@launch
            val result = sync()
            if (result == SyncResult.Failed && schedule.value == null) {
                _messages.send(UiMessage("Impossibile scaricare l'orario. Controlla la connessione."))
            }
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val result = sync()
            _refreshing.value = false
            _messages.send(
                UiMessage(when (result) {
                    SyncResult.Updated -> "Orario aggiornato"
                    SyncResult.Unchanged -> "L'orario è già aggiornato"
                    SyncResult.Failed -> "Impossibile aggiornare. Controlla la connessione."
                })
            )
        }
    }

    private suspend fun sync(): SyncResult {
        _syncing.value = true
        val schedule = schedules.sync()
        val substitutions = overridesRepository.sync()
        _syncing.value = false
        // Le supplenze arrivate dal foglio contano come aggiornamento; il fallimento è quello dell'orario
        return if (schedule == SyncResult.Unchanged && substitutions == SyncResult.Updated) SyncResult.Updated else schedule
    }

    /** Supplenza ([lesson] non nullo) o ora libera ([lesson] nullo), solo per [date]. */
    fun setOverride(date: LocalDate, hour: Int, lesson: Lesson?) = viewModelScope.launch {
        val key = OverrideKey(date, hour)
        val previous = overrides.value[key]
        overridesRepository.set(Override.of(date, hour, lesson))
        launch { overridesRepository.sync() }
        if (lesson == null) {
            _messages.send(UiMessage("Ora libera solo per questo giorno", "Annulla") { restore(key, previous) })
        }
    }

    fun clearOverride(date: LocalDate, hour: Int) = viewModelScope.launch {
        val key = OverrideKey(date, hour)
        val previous = overrides.value[key] ?: return@launch
        overridesRepository.remove(key)
        launch { overridesRepository.sync() }
        _messages.send(UiMessage("Orario ripristinato", "Annulla") { restore(key, previous) })
    }

    private fun restore(key: OverrideKey, previous: Override?) = viewModelScope.launch {
        if (previous != null) overridesRepository.set(previous) else overridesRepository.remove(key)
        overridesRepository.sync()
    }

    fun setManualReminder(reminder: ManualReminder) = viewModelScope.launch { remindersRepository.set(reminder) }

    fun removeManualReminder(reminder: ManualReminder) = viewModelScope.launch {
        remindersRepository.remove(reminder)
        _messages.send(UiMessage("Promemoria eliminato", "Annulla") { setManualReminder(reminder) })
    }

    fun setBlockReminders(enabled: Boolean, lead: Int) = viewModelScope.launch { settingsRepository.setBlockReminders(enabled, lead) }
    fun setLunchReminders(enabled: Boolean, lead: Int) = viewModelScope.launch { settingsRepository.setLunchReminders(enabled, lead) }
    fun setPause(from: LocalDate?, to: LocalDate?) = viewModelScope.launch { settingsRepository.setPause(from, to) }

    fun setWeeklyView(weekly: Boolean) = viewModelScope.launch { settingsRepository.setWeeklyView(weekly) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setSeedColor(color: Int) = viewModelScope.launch { settingsRepository.setSeedColor(color) }

    private companion object {
        const val FOREGROUND_SYNC_INTERVAL = 15 * 60 * 1000L
    }
}
