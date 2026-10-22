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
import java.time.LocalDateTime

/**
 * Promemoria impostato a mano su un'ora. Senza [date] vale ogni settimana per quel giorno
 * (es. ogni lunedì alla 2ª ora); con [date] solo per quella data (es. una supplenza).
 */
@Serializable
data class ManualReminder(
    val dayName: String,
    val hour: Int,
    val lead: Int,
    val date: String? = null,
) {
    fun matches(day: Day, date: LocalDate, hour: Int) =
        this.hour == hour && if (this.date != null) this.date == date.toString() else dayName.normalized() == day.name.normalized()
}

data class ReminderSettings(
    val blockEnabled: Boolean,
    val blockLead: Int,
    val lunchEnabled: Boolean,
    val lunchLead: Int,
    val pauseFrom: LocalDate?,
    val pauseTo: LocalDate?,
) {
    fun isPaused(date: LocalDate) = pauseFrom != null && pauseTo != null && date in pauseFrom..pauseTo
}

enum class ReminderKind { Block, Lunch, Manual }

data class Reminder(
    val kind: ReminderKind,
    val date: LocalDate,
    val slot: LessonSlot,
    val lesson: Lesson,
    val lead: Int,
    val manual: ManualReminder? = null,
) {
    val startAt: LocalDateTime get() = date.atTime(slot.start / 60, slot.start % 60)
    val triggerAt: LocalDateTime get() = startAt.minusMinutes(lead.toLong())
}

/**
 * Tutti i promemoria dei [days] giorni a partire da [from], con supplenze e ore libere applicate.
 *
 * - Inizio blocco: prima lezione del giorno o lezione dopo un'ora libera (l'intervallo non conta).
 * - Dopo pranzo: lezione subito dopo la pausa pranzo, se anche l'ora prima di pranzo era una lezione.
 * - Manuali: sulle ore scelte, solo se quel giorno c'è davvero lezione.
 */
fun computeReminders(
    schedule: Schedule,
    overrides: Map<OverrideKey, Override>,
    manual: List<ManualReminder>,
    settings: ReminderSettings,
    from: LocalDate,
    days: Int,
): List<Reminder> = buildList {
    for (offset in 0 until days) {
        val date = from.plusDays(offset.toLong())
        if (settings.isPaused(date)) continue
        val day = schedule.days.firstOrNull { it.dayOfWeek == date.dayOfWeek } ?: continue
        val effective = day.forDate(date, overrides)
        val slots = effective.timeline().filterIsInstance<LessonSlot>()

        slots.forEachIndexed { index, slot ->
            val lesson = slot.lesson ?: return@forEachIndexed
            val previous = slots.getOrNull(index - 1)
            val afterLunch = effective.config.lunchDuration > 0 && effective.config.lunchAfter == slot.hour
            when {
                previous?.lesson == null ->
                    if (settings.blockEnabled) add(Reminder(ReminderKind.Block, date, slot, lesson, settings.blockLead))
                afterLunch ->
                    if (settings.lunchEnabled) add(Reminder(ReminderKind.Lunch, date, slot, lesson, settings.lunchLead))
            }
            manual.filter { it.matches(day, date, slot.hour) }.forEach {
                add(Reminder(ReminderKind.Manual, date, slot, lesson, it.lead, it))
            }
        }
    }
}.sortedBy { it.triggerAt }

class RemindersRepository(dir: File) {

    private val file = File(dir, "reminders.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _reminders = MutableStateFlow<List<ManualReminder>>(emptyList())
    val reminders: StateFlow<List<ManualReminder>> = _reminders.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            val saved = runCatching { json.decodeFromString<List<ManualReminder>>(file.readText()) }.getOrDefault(emptyList())
            // I promemoria di un solo giorno già passati non servono più
            val today = LocalDate.now().toString()
            _reminders.value = saved.filter { it.date == null || it.date >= today }
            _loaded.value = true
        }
    }

    /** Aggiunge o sostituisce il promemoria per quell'ora (stesso giorno, ora e data). */
    suspend fun set(reminder: ManualReminder) = update { list ->
        list.filterNot { it.sameSlot(reminder) } + reminder
    }

    suspend fun remove(reminder: ManualReminder) = update { list -> list.filterNot { it.sameSlot(reminder) } }

    private fun ManualReminder.sameSlot(other: ManualReminder) =
        hour == other.hour && date == other.date && dayName.normalized() == other.dayName.normalized()

    private suspend fun update(change: (List<ManualReminder>) -> List<ManualReminder>) = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            val updated = change(_reminders.value)
            _reminders.value = updated
            runCatching { file.writeText(json.encodeToString(updated)) }
                .onFailure { Log.w("RemindersRepository", "Salvataggio fallito", it) }
        }
    }
}
