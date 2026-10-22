package io.github.lk024.orario.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lk024.orario.OrarioApplication
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val schedules = (application as OrarioApplication).scheduleRepository
    private val settingsRepository = (application as OrarioApplication).settingsRepository

    val schedule = schedules.schedule
    val lastSync = schedules.lastSync
    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Lo splash resta finché non ci sono sia l'orario in cache sia le impostazioni: niente sfarfallii. */
    val ready: StateFlow<Boolean> = combine(schedules.loaded, settings) { loaded, settings -> loaded && settings != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    /** Solo per l'aggiornamento chiesto dall'utente (pull-to-refresh o pulsante). */
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var backgroundSync: Job? = null

    init {
        viewModelScope.launch { schedules.load() }
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
                _messages.send("Impossibile scaricare l'orario. Controlla la connessione.")
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
                when (result) {
                    SyncResult.Updated -> "Orario aggiornato"
                    SyncResult.Unchanged -> "L'orario è già aggiornato"
                    SyncResult.Failed -> "Impossibile aggiornare. Controlla la connessione."
                }
            )
        }
    }

    private suspend fun sync(): SyncResult {
        _syncing.value = true
        return schedules.sync().also { _syncing.value = false }
    }

    fun setWeeklyView(weekly: Boolean) = viewModelScope.launch { settingsRepository.setWeeklyView(weekly) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setSeedColor(color: Int) = viewModelScope.launch { settingsRepository.setSeedColor(color) }

    private companion object {
        const val FOREGROUND_SYNC_INTERVAL = 15 * 60 * 1000L
    }
}
