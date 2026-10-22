package io.github.lk024.orario.data

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.time.DayOfWeek

@Serializable
data class Lesson(
    val title: String,
    val room: String? = null,
    val teacher: String? = null,
)

/** Orari di un giorno. Tutti i tempi sono in minuti, [start] è in minuti dalla mezzanotte. */
@Serializable
data class DayConfig(
    val totalHours: Int,
    val durations: List<Int>,
    val lunchDuration: Int = 0,
    val lunchAfter: Int = 0,
    val start: Int = DEFAULT_START,
    val breakDuration: Int = DEFAULT_BREAK_DURATION,
    val breakAfter: Int = DEFAULT_BREAK_AFTER,
) {
    companion object {
        const val DEFAULT_START = 8 * 60 + 20
        const val DEFAULT_BREAK_DURATION = 20
        const val DEFAULT_BREAK_AFTER = 3

        /** Stessi valori di fallback della PWA, usati se il foglio non ha la riga del giorno. */
        val Fallback = DayConfig(totalHours = 6, durations = listOf(60, 60, 60, 50, 50, 50))
    }
}

@Serializable
data class Day(
    val name: String,
    val lessons: List<Lesson?>,
    val config: DayConfig,
) {
    val dayOfWeek: DayOfWeek? get() = dayOfWeekFromName(name)
}

@Serializable
data class Schedule(val days: List<Day>)

enum class BreakKind(val label: String) { Recess("Intervallo"), Lunch("Pranzo") }

sealed interface Slot {
    val start: Int
    val end: Int
    operator fun contains(minute: Int) = minute in start until end
}

data class LessonSlot(val hour: Int, val lesson: Lesson?, override val start: Int, override val end: Int) : Slot
data class BreakSlot(val kind: BreakKind, override val start: Int, override val end: Int) : Slot

/** Sequenza di ore e pause del giorno, con orari calcolati come nella PWA. */
fun Day.timeline(): List<Slot> = buildList {
    var t = config.start
    for (i in 0 until config.totalHours) {
        val duration = config.durations.getOrElse(i) { 0 }
        add(LessonSlot(i, lessons.getOrNull(i), t, t + duration))
        t += duration
        if (i == config.totalHours - 1) break
        if (config.breakAfter > 0 && i == config.breakAfter - 1 && config.breakDuration > 0) {
            add(BreakSlot(BreakKind.Recess, t, t + config.breakDuration))
            t += config.breakDuration
        }
        if (config.lunchAfter > 0 && i == config.lunchAfter - 1 && config.lunchDuration > 0) {
            add(BreakSlot(BreakKind.Lunch, t, t + config.lunchDuration))
            t += config.lunchDuration
        }
    }
}

fun formatMinutes(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)

internal fun String.normalized(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

private val dayNames = mapOf(
    "lunedi" to DayOfWeek.MONDAY,
    "martedi" to DayOfWeek.TUESDAY,
    "mercoledi" to DayOfWeek.WEDNESDAY,
    "giovedi" to DayOfWeek.THURSDAY,
    "venerdi" to DayOfWeek.FRIDAY,
    "sabato" to DayOfWeek.SATURDAY,
    "domenica" to DayOfWeek.SUNDAY,
)

fun dayOfWeekFromName(name: String): DayOfWeek? {
    val n = name.normalized()
    return dayNames[n] ?: dayNames.entries.firstOrNull { n.length >= 3 && it.key.startsWith(n) }?.value
}
