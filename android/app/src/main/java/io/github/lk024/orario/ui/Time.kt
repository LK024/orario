package io.github.lk024.orario.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.lk024.orario.data.Day
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Ora corrente al minuto. Scatta allo scoccare di ogni minuto e si ricalcola subito
 * quando l'app torna in primo piano; se il minuto non è cambiato non ridisegna nulla.
 */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val now = remember { mutableStateOf(currentMinute()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = currentMinute()
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }
    return now
}

private fun currentMinute() = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

val LocalDateTime.minuteOfDay get() = hour * 60 + minute

/** Il lunedì della settimana da mostrare: nel weekend è già quella successiva. */
fun displayedWeekStart(today: LocalDate): LocalDate =
    if (today.dayOfWeek >= DayOfWeek.SATURDAY) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun Day.dateIn(weekStart: LocalDate, index: Int): LocalDate =
    weekStart.plusDays((dayOfWeek?.ordinal ?: index).toLong())

/** Indice del giorno da aprire: oggi, oppure il primo giorno della settimana. */
fun List<Day>.todayIndex(today: LocalDate): Int {
    val weekStart = displayedWeekStart(today)
    return withIndex().firstOrNull { (i, day) -> day.dateIn(weekStart, i) == today }?.index ?: 0
}

private val italian = Locale.ITALIAN
val DayMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", italian)
private val TimeFormatter = DateTimeFormatter.ofPattern("H:mm", italian)

fun formatLastSync(millis: Long, today: LocalDate): String {
    val dateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
    val time = dateTime.format(TimeFormatter)
    return when (dateTime.toLocalDate()) {
        today -> "oggi alle $time"
        today.minusDays(1) -> "ieri alle $time"
        else -> "${dateTime.format(DayMonthFormatter)} alle $time"
    }
}
