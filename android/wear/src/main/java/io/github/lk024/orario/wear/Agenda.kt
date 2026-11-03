package io.github.lk024.orario.wear

import io.github.lk024.orario.data.BreakSlot
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.Override
import io.github.lk024.orario.data.OverrideKey
import io.github.lk024.orario.data.Schedule
import io.github.lk024.orario.data.Slot
import io.github.lk024.orario.data.forDate
import io.github.lk024.orario.data.timeline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** La giornata di una data, con supplenze e ore libere già applicate. */
data class DayAgenda(val date: LocalDate, val name: String, val slots: List<Slot>) {
    val lessons get() = slots.filterIsInstance<LessonSlot>().filter { it.lesson != null }
}

fun Schedule.agenda(date: LocalDate, overrides: Map<OverrideKey, Override>): DayAgenda? {
    val day = days.firstOrNull { it.dayOfWeek == date.dayOfWeek } ?: return null
    return DayAgenda(date, day.name, day.forDate(date, overrides).timeline())
}

/** Cosa mostrare in un certo momento su Tile e complicazione. */
sealed interface Status {
    data class InLesson(val slot: LessonSlot, val lesson: Lesson) : Status
    data class InPause(val pause: BreakSlot, val next: LessonSlot) : Status
    data class Next(val date: LocalDate, val slot: LessonSlot, val lesson: Lesson, val today: Boolean) : Status
    data object Nothing : Status
}

fun Schedule.statusAt(at: LocalDateTime, overrides: Map<OverrideKey, Override>): Status {
    val minute = at.hour * 60 + at.minute
    agenda(at.toLocalDate(), overrides)?.let { today ->
        val current = today.slots.firstOrNull { minute in it }
        val nextToday = today.lessons.firstOrNull { it.start >= (current?.end ?: minute) && it.start >= minute }
        if (current is LessonSlot && current.lesson != null) return Status.InLesson(current, current.lesson!!)
        if (current is BreakSlot && nextToday != null) return Status.InPause(current, nextToday)
        if (nextToday != null) return Status.Next(today.date, nextToday, nextToday.lesson!!, today = true)
    }
    for (offset in 1..7L) {
        val first = agenda(at.toLocalDate().plusDays(offset), overrides)?.lessons?.firstOrNull() ?: continue
        return Status.Next(at.toLocalDate().plusDays(offset), first, first.lesson!!, today = false)
    }
    return Status.Nothing
}

/**
 * Gli istanti di oggi (da `from` in poi) in cui lo stato cambia: inizio e fine di ogni ora e pausa.
 * Servono a Tile e complicazione per preparare in anticipo cosa mostrare, senza aggiornamenti.
 */
fun Schedule.changesToday(from: LocalDateTime, overrides: Map<OverrideKey, Override>): List<LocalDateTime> {
    val today = agenda(from.toLocalDate(), overrides) ?: return emptyList()
    return today.slots.flatMap { listOf(it.start, it.end) }
        .distinct()
        .sorted()
        .map { from.toLocalDate().atTime(it / 60, it % 60) }
        .filter { it > from }
}

fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private val italian = Locale.ITALIAN
private val shortDay = DateTimeFormatter.ofPattern("EEE d", italian)
private val fullDay = DateTimeFormatter.ofPattern("EEEE d MMMM", italian)

fun LocalDate.shortLabel(): String = format(shortDay).replaceFirstChar(Char::uppercase)
fun LocalDate.fullLabel(): String = format(fullDay).replaceFirstChar(Char::uppercase)
fun LocalDate.weekdayLabel(): String =
    format(DateTimeFormatter.ofPattern("EEEE", italian)).replaceFirstChar(Char::uppercase)

/** Cosa mostra il Tile in un certo momento. */
sealed interface TileState {
    /** Una giornata di lezioni: oggi ([live], con countdown) oppure la prossima, con il suo titolo. */
    data class Lessons(val agenda: DayAgenda, val title: String?, val live: Boolean) : TileState

    /** Schermata di riposo ("Sei libero", "Buon weekend") con la prima lezione che verrà. */
    data class Rest(val headline: String, val nextDate: LocalDate, val nextLesson: LessonSlot, val nextLabel: String) : TileState

    data object Empty : TileState
}

/** Dopo l'ultima lezione "Sei libero" resta per questo tempo, poi compare il giorno dopo. */
const val REST_MINUTES = 60

/** Se il prossimo giorno di scuola non è domani (weekend), il suo orario compare dalla sera prima a quest'ora. */
const val EVENING_BEFORE_HOUR = 18

fun Schedule.tileState(at: LocalDateTime, overrides: Map<OverrideKey, Override>): TileState {
    val today = at.toLocalDate()
    val minute = at.hour * 60 + at.minute
    val todayAgenda = agenda(today, overrides)?.takeIf { it.lessons.isNotEmpty() }
    val next = (1..14L).firstNotNullOfOrNull { offset ->
        agenda(today.plusDays(offset), overrides)?.takeIf { it.lessons.isNotEmpty() }
    }

    if (todayAgenda != null) {
        val lastEnd = todayAgenda.lessons.last().end
        if (minute < lastEnd) return TileState.Lessons(todayAgenda, title = null, live = true)
        if (minute < lastEnd + REST_MINUTES) return rest("Sei libero", today, next)
    }
    next ?: return TileState.Empty
    val gap = next.date.toEpochDay() - today.toEpochDay()
    if (gap == 1L) return TileState.Lessons(next, title = "Domani", live = false)
    val evening = next.date.minusDays(1).atTime(EVENING_BEFORE_HOUR, 0)
    return if (at >= evening) TileState.Lessons(next, title = next.date.weekdayLabel(), live = false)
    else rest(if (crossesWeekend(today, next.date)) "Buon weekend" else "Giornata libera", today, next)
}

private fun rest(headline: String, today: LocalDate, next: DayAgenda?): TileState {
    next ?: return TileState.Empty
    val first = next.lessons.first()
    val day = if (next.date == today.plusDays(1)) "Domani" else next.date.weekdayLabel()
    return TileState.Rest(headline, next.date, first, "$day alle ${io.github.lk024.orario.data.formatMinutes(first.start)} · ${first.lesson!!.title}")
}

private fun crossesWeekend(from: LocalDate, to: LocalDate) =
    generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }
        .any { it.dayOfWeek == java.time.DayOfWeek.SATURDAY || it.dayOfWeek == java.time.DayOfWeek.SUNDAY }

/** Gli istanti in cui il Tile cambia aspetto, da `from` in poi (ore, pause, "Sei libero", sera prima). */
fun Schedule.tileChanges(from: LocalDateTime, overrides: Map<OverrideKey, Override>): List<LocalDateTime> {
    val today = from.toLocalDate()
    val moments = mutableListOf<LocalDateTime>()
    agenda(today, overrides)?.takeIf { it.lessons.isNotEmpty() }?.let { agenda ->
        val first = agenda.lessons.first().start
        val last = agenda.lessons.last().end
        agenda.slots.filter { it.start >= first && it.end <= last }
            .flatMap { listOf(it.start, it.end) }
            .forEach { moments += today.atTime(it / 60, it % 60) }
        moments += today.atTime(0, 0).plusMinutes((last + REST_MINUTES).toLong())
    }
    (0..14L).forEach { moments += today.plusDays(it).atTime(EVENING_BEFORE_HOUR, 0) }
    moments += today.plusDays(1).atStartOfDay()
    return moments.distinct().filter { it > from }.sorted()
}
