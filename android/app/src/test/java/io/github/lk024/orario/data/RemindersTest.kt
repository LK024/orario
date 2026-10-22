package io.github.lk024.orario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val friday = LocalDate.of(2026, 10, 23)

    private val schedule = Schedule(listOf(
        // Lunedì: 1ª e 2ª, 3ª libera, 4ª e 5ª (dopo l'intervallo e l'ora libera)
        Day("Lunedì", listOf(Lesson("3B"), Lesson("5D"), null, Lesson("1E"), Lesson("3E"), null), DayConfig.Fallback),
        // Venerdì come nel foglio vero: 5ª, 6ª, pranzo, 7ª, 8ª libera, 9ª
        Day(
            "Venerdì",
            listOf(null, null, null, null, Lesson("5D"), Lesson("3D"), Lesson("5E"), null, Lesson("1E")),
            DayConfig(9, listOf(60, 50, 50, 50, 50, 50, 50, 50, 50), lunchDuration = 35, lunchAfter = 6),
        ),
    ))

    private val settings = ReminderSettings(
        blockEnabled = true, blockLead = 30, lunchEnabled = true, lunchLead = 10, pauseFrom = null, pauseTo = null,
    )

    private fun List<Reminder>.describe() = map { "${it.kind} ${it.lesson.title} ${it.triggerAt.toLocalTime()}" }

    @Test
    fun blockStartsAfterFreeHourButNotAfterRecess() {
        val reminders = computeReminders(schedule, emptyMap(), emptyList(), settings, monday, 1)
        // 3B 8:20 → 7:50; 1E 11:40 (dopo 3ª libera + intervallo) → 11:10
        assertEquals(listOf("Block 3B 07:50", "Block 1E 11:10"), reminders.describe())
    }

    @Test
    fun fridayHasBlockLunchAndBlockAfterFreeHour() {
        val reminders = computeReminders(schedule, emptyMap(), emptyList(), settings, friday, 1)
        assertEquals(listOf("Block 5D 11:40", "Lunch 5E 14:15", "Block 1E 15:35"), reminders.describe())
    }

    @Test
    fun lunchAfterFreeHourCountsAsBlock() {
        val noSixth = mapOf(OverrideKey(friday, 5) to Override.of(friday, 5, null))
        val reminders = computeReminders(schedule, noSixth, emptyList(), settings, friday, 1)
        assertEquals(listOf("Block 5D 11:40", "Block 5E 13:55", "Block 1E 15:35"), reminders.describe())
    }

    @Test
    fun substitutionInFreeHourStartsABlock() {
        val sub = mapOf(OverrideKey(monday, 2) to Override.of(monday, 2, Lesson("2A")))
        val reminders = computeReminders(schedule, sub, emptyList(), settings, monday, 1)
        // La 3ª diventa una lezione: il blocco è unico dalla 1ª alla 5ª
        assertEquals(listOf("Block 3B 07:50"), reminders.describe())
    }

    @Test
    fun manualReminderIsWeeklyAndSkipsFreedHours() {
        val manual = listOf(ManualReminder("Lunedì", 1, 15))
        val twoWeeks = computeReminders(schedule, emptyMap(), manual, settings.copy(blockEnabled = false, lunchEnabled = false), monday, 8)
        assertEquals(listOf("Manual 5D 09:05", "Manual 5D 09:05"), twoWeeks.describe())

        val freed = mapOf(OverrideKey(monday, 1) to Override.of(monday, 1, null))
        assertTrue(computeReminders(schedule, freed, manual, settings.copy(blockEnabled = false, lunchEnabled = false), monday, 1).isEmpty())
    }

    @Test
    fun oneOffManualReminderOnlyOnItsDate() {
        val manual = listOf(ManualReminder("Lunedì", 0, 5, date = monday.toString()))
        val reminders = computeReminders(schedule, emptyMap(), manual, settings.copy(blockEnabled = false, lunchEnabled = false), monday, 8)
        assertEquals(listOf("Manual 3B 08:15"), reminders.describe())
    }

    @Test
    fun pauseSkipsEverythingInRange() {
        val paused = settings.copy(pauseFrom = monday, pauseTo = monday.plusDays(4))
        val manual = listOf(ManualReminder("Lunedì", 1, 15))
        val reminders = computeReminders(schedule, emptyMap(), manual, paused, monday, 8)
        // Solo il lunedì della settimana dopo
        assertTrue(reminders.all { it.date == monday.plusWeeks(1) })
        assertEquals(3, reminders.size)
    }
}
