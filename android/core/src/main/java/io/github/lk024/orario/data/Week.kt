package io.github.lk024.orario.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
