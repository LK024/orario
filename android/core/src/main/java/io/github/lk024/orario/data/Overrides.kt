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
import java.time.LocalDate

/**
 * Variazione dell'orario valida solo per una data: una supplenza ([lesson] non nullo)
 * oppure un'ora resa libera ([lesson] nullo). L'orario fisso del foglio non cambia.
 */
@Serializable
data class Override(
    val date: String,
    val hour: Int,
    val lesson: Lesson?,
) {
    val key get() = OverrideKey(LocalDate.parse(date), hour)

    companion object {
        fun of(date: LocalDate, hour: Int, lesson: Lesson?) = Override(date.toString(), hour, lesson)
    }
}

data class OverrideKey(val date: LocalDate, val hour: Int)

/** Il giorno con le variazioni di [date] applicate. */
fun Day.forDate(date: LocalDate, overrides: Map<OverrideKey, Override>): Day {
    if (overrides.keys.none { it.date == date }) return this
    val hours = maxOf(lessons.size, config.totalHours)
    return copy(lessons = List(hours) { hour ->
        val key = OverrideKey(date, hour)
        if (key in overrides) overrides.getValue(key).lesson else lessons.getOrNull(hour)
    })
}

@Serializable
private data class StoredOverrides(
    val overrides: List<Override>,
    val pending: List<PendingChange> = emptyList(),
)

/**
 * Le variazioni vivono sul telefono e, se la sincronizzazione è configurata, anche nella scheda
 * "Supplenze" del foglio. Le modifiche fatte offline restano in coda e partono alla prima occasione.
 */
class OverridesRepository(dir: File, private val api: SubstitutionsApi) {

    private val file = File(dir, "overrides.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val syncMutex = Mutex()

    private val _overrides = MutableStateFlow<Map<OverrideKey, Override>>(emptyMap())
    val overrides: StateFlow<Map<OverrideKey, Override>> = _overrides.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var pending: List<PendingChange> = emptyList()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            val text = runCatching { file.readText() }.getOrNull()
            val stored = text?.let {
                runCatching { json.decodeFromString<StoredOverrides>(it) }.getOrNull()
                    // Formato della prima versione (solo elenco, senza coda): tutto va caricato nel foglio
                    ?: runCatching { json.decodeFromString<List<Override>>(it) }.getOrNull()
                        ?.let { list -> StoredOverrides(list, list.map { o -> PendingChange(o.date, o.hour, o.lesson) }) }
            } ?: StoredOverrides(emptyList())
            pending = stored.pending
            _overrides.value = recent(stored.overrides.associateBy { it.key })
            _loaded.value = true
        }
    }

    suspend fun set(override: Override) =
        change(PendingChange(override.date, override.hour, override.lesson)) { it + (override.key to override) }

    suspend fun remove(key: OverrideKey) =
        change(PendingChange(key.date.toString(), key.hour, delete = true)) { it - key }

    /** Invia le modifiche in coda e scarica le supplenze dal foglio. */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        if (!api.enabled) return@withContext SyncResult.Unchanged
        load()
        syncMutex.withLock {
            val sent = mutex.withLock { pending }
            val remote = runCatching { if (sent.isEmpty()) api.fetch() else api.push(sent) }
                .onFailure { Log.w(TAG, "Sync supplenze fallito", it) }
                .getOrNull() ?: return@withLock SyncResult.Failed
            mutex.withLock {
                val before = _overrides.value
                // Quello che è cambiato mentre la richiesta era in corso resta in coda
                pending = pending.filterNot { it in sent }
                _overrides.value = recent(mergeWithPending(remote, pending))
                save()
                if (_overrides.value != before) SyncResult.Updated else SyncResult.Unchanged
            }
        }
    }

    private suspend fun change(
        pendingChange: PendingChange,
        update: (Map<OverrideKey, Override>) -> Map<OverrideKey, Override>,
    ) = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            _overrides.value = update(_overrides.value)
            pending = pending.filterNot { it.key == pendingChange.key } + pendingChange
            save()
        }
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(StoredOverrides(_overrides.value.values.toList(), pending))) }
            .onFailure { Log.w(TAG, "Salvataggio fallito", it) }
    }

    /** Sul telefono bastano le ultime due settimane: lo storico completo resta nel foglio. */
    private fun recent(overrides: Map<OverrideKey, Override>): Map<OverrideKey, Override> {
        val oldest = LocalDate.now().minusDays(14)
        return overrides.filterKeys { it.date >= oldest }
    }

    private companion object {
        const val TAG = "OverridesRepository"
    }
}
