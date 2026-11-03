package io.github.lk024.orario.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class SyncResult { Updated, Unchanged, Failed }

@Serializable
private data class CachedSchedule(val schedule: Schedule, val syncedAt: Long)

/**
 * L'orario vive in un file locale: l'interfaccia mostra sempre quello e la rete
 * serve solo ad aggiornarlo. Se il CSV scaricato è identico non cambia nulla,
 * quindi lo schermo non si ridisegna.
 */
class ScheduleRepository(cacheDir: File, private val csvUrl: String) {

    private val file = File(cacheDir, "schedule.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _schedule = MutableStateFlow<Schedule?>(null)
    val schedule: StateFlow<Schedule?> = _schedule.asStateFlow()

    private val _lastSync = MutableStateFlow<Long?>(null)
    val lastSync: StateFlow<Long?> = _lastSync.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            runCatching { json.decodeFromString<CachedSchedule>(file.readText()) }
                .onSuccess {
                    _schedule.value = it.schedule
                    _lastSync.value = it.syncedAt
                }
            _loaded.value = true
        }
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            val downloaded = runCatching { ScheduleParser.parse(download()) }
                .onFailure { Log.w(TAG, "Sync fallito", it) }
                .getOrNull() ?: return@withLock SyncResult.Failed

            val now = System.currentTimeMillis()
            val changed = downloaded != _schedule.value
            runCatching { file.writeText(json.encodeToString(CachedSchedule(downloaded, now))) }
                .onFailure { Log.w(TAG, "Salvataggio cache fallito", it) }
            _schedule.value = downloaded
            _lastSync.value = now
            if (changed) SyncResult.Updated else SyncResult.Unchanged
        }
    }

    private fun download(): String {
        val connection = URL("$csvUrl&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ScheduleRepository"
    }
}
