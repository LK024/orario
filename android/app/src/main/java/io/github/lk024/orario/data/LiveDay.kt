package io.github.lk024.orario.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Una riga dell'elenco della giornata nella notifica espansa. */
data class LiveLine(val text: String, val current: Boolean)

/**
 * Contenuto del Live Update della giornata, con le stesse informazioni della barra dei menu del Mac:
 * lezione in corso ("3B fino 10:20") o, in pausa e nelle ore buche, la prossima ("5D alle 12:10").
 * Sul telefono la pillola resta vuota perché il testo è lungo, ma sull'orologio si legge per intero.
 */
data class LiveDay(
    val pill: String,
    val title: String,
    val text: String,
    val lines: List<LiveLine>,
)

/** Nella tendina il sistema mostra solo le prime 4 righe dell'elenco e taglia le altre senza avvisare. */
private const val MAX_LIVE_LINES = 4

/** Il Live Update di [now], oppure null se non va mostrato (fuori dalle lezioni, giorno libero, pausa). */
fun liveDay(
    schedule: Schedule,
    overrides: Map<OverrideKey, Override>,
    settings: ReminderSettings,
    now: LocalDateTime,
): LiveDay? {
    val date = now.toLocalDate()
    val minute = now.hour * 60 + now.minute
    if (settings.isPaused(date)) return null
    val slots = daySlots(schedule, overrides, date) ?: return null
    if (minute < slots.first().start || minute >= slots.last().end) return null

    val current = slots.first { minute in it }
    val (pill, title, text) = if (current is LessonSlot && current.lesson != null) {
        val lesson = current.lesson!!
        Triple(
            "${lesson.title} fino ${formatMinutes(current.end)}",
            "${lesson.title} · fino alle ${formatMinutes(current.end)}",
            listOfNotNull(lesson.room, lesson.teacher).joinToString(" · ").ifEmpty { "In corso" },
        )
    } else {
        // In pausa o in un'ora libera: la prossima lezione (c'è sempre, la giornata finisce con una lezione)
        val next = slots.filterIsInstance<LessonSlot>().first { it.lesson != null && it.start >= current.end }
        Triple(
            "${next.lesson!!.title} alle ${formatMinutes(next.start)}",
            "${next.lesson!!.title} alle ${formatMinutes(next.start)}",
            if (current is BreakSlot) "${current.kind.label} fino alle ${formatMinutes(current.end)}" else "Ora libera",
        )
    }
    // Le ore passate spariscono; se il resto non ci sta, le ultime si riassumono nella riga di fine
    val remaining = slots.dropWhile { it != current }
    val shown = remaining.take(if (remaining.size > MAX_LIVE_LINES) MAX_LIVE_LINES - 1 else MAX_LIVE_LINES)
    val hidden = remaining.drop(shown.size).count { it is LessonSlot }
    val lines = shown.map { slot ->
        val text = when (slot) {
            is BreakSlot -> "${if (slot.kind == BreakKind.Lunch) "🍴" else "☕"}  ${formatMinutes(slot.start)}–${formatMinutes(slot.end)}"
            is LessonSlot -> {
                val changed = OverrideKey(date, slot.hour) in overrides
                val lesson = slot.lesson
                val name = when {
                    lesson == null -> if (changed) "Libera oggi" else "Libera"
                    else -> listOfNotNull(lesson.title, lesson.room).joinToString(" · ") + if (changed) " · supplenza" else ""
                }
                "${formatMinutes(slot.start)}  $name"
            }
        }
        LiveLine(text, current = slot == current)
    }
    val end = if (hidden > 0) listOf(LiveLine(dayEndLabel(slots.last().end, hidden), current = false)) else emptyList()
    return LiveDay(pill, title, text, lines + end)
}

/**
 * Quando il Live Update va ridisegnato: alla fine dell'ora o pausa in corso, oppure all'inizio della
 * prima lezione del prossimo giorno di scuola (saltando i giorni in pausa).
 */
fun nextLiveDayChange(
    schedule: Schedule,
    overrides: Map<OverrideKey, Override>,
    settings: ReminderSettings,
    now: LocalDateTime,
): LocalDateTime? {
    val minute = now.hour * 60 + now.minute
    for (offset in 0..14L) {
        val date = now.toLocalDate().plusDays(offset)
        if (settings.isPaused(date)) continue
        val slots = daySlots(schedule, overrides, date) ?: continue
        val change = slots.flatMap { listOf(it.start, it.end) }
            .firstOrNull { offset > 0 || it > minute } ?: continue
        return date.atTime(change / 60, change % 60)
    }
    return null
}

/** Ore e pause di una data dalla prima all'ultima lezione (le ore buche ai bordi non contano). */
private fun daySlots(schedule: Schedule, overrides: Map<OverrideKey, Override>, date: LocalDate): List<Slot>? {
    val day = schedule.days.firstOrNull { it.dayOfWeek == date.dayOfWeek } ?: return null
    val slots = day.forDate(date, overrides).timeline()
    val lessons = slots.filterIsInstance<LessonSlot>().filter { it.lesson != null }
    if (lessons.isEmpty()) return null
    return slots.filter { it.start >= lessons.first().start && it.end <= lessons.last().end }
}
