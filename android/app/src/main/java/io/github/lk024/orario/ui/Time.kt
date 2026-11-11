package io.github.lk024.orario.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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

private val italian = Locale.ITALIAN
val DayMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", italian)
private val TimeFormatter = DateTimeFormatter.ofPattern("H:mm", italian)
private val ShortDayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", italian)

/** "16–20 nov" oppure "30 nov – 4 dic": breve, per stare nella barra in alto. */
fun weekRangeLabel(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) "${start.dayOfMonth}–${end.format(ShortDayMonthFormatter)}"
    else "${start.format(ShortDayMonthFormatter)} – ${end.format(ShortDayMonthFormatter)}"

fun formatLastSync(millis: Long, today: LocalDate): String {
    val dateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
    val time = dateTime.format(TimeFormatter)
    return when (dateTime.toLocalDate()) {
        today -> "oggi alle $time"
        today.minusDays(1) -> "ieri alle $time"
        else -> "${dateTime.format(DayMonthFormatter)} alle $time"
    }
}
