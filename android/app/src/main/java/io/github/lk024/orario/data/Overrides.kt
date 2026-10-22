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

class OverridesRepository(dir: File) {

    private val file = File(dir, "overrides.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _overrides = MutableStateFlow<Map<OverrideKey, Override>>(emptyMap())
    val overrides: StateFlow<Map<OverrideKey, Override>> = _overrides.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            val saved = runCatching { json.decodeFromString<List<Override>>(file.readText()) }.getOrDefault(emptyList())
            // Le variazioni passate non servono più: si tengono solo quelle delle ultime due settimane
            val oldest = LocalDate.now().minusDays(14)
            _overrides.value = saved.associateBy { it.key }.filterKeys { it.date >= oldest }
            _loaded.value = true
        }
    }

    suspend fun set(override: Override) = update { it + (override.key to override) }

    suspend fun remove(key: OverrideKey) = update { it - key }

    private suspend fun update(change: (Map<OverrideKey, Override>) -> Map<OverrideKey, Override>) =
        withContext(Dispatchers.IO) {
            load()
            mutex.withLock {
                val updated = change(_overrides.value)
                _overrides.value = updated
                runCatching { file.writeText(json.encodeToString(updated.values.toList())) }
                    .onFailure { Log.w("OverridesRepository", "Salvataggio fallito", it) }
            }
        }
}
