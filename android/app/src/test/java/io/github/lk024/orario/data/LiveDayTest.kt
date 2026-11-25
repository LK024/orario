package io.github.lk024.orario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LiveDayTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val schedule = Schedule(listOf(
        // 1ª libera, 2ª 3B, 3ª 5D, intervallo, 4ª libera, 5ª 1E, 6ª libera
        Day("Lunedì", listOf(null, Lesson("3B", "Lab 2"), Lesson("5D", "2.03", "Bonelli"), null, Lesson("1E"), null), DayConfig.Fallback),
    ))
    private val settings = ReminderSettings(false, 30, false, 10, null, null)

    private fun at(h: Int, m: Int, date: LocalDate = monday) = LocalDateTime.of(date, LocalTime.of(h, m))

    @Test
    fun hiddenOutsideTheLessons() {
        // Prima lezione 9:20 (la 1ª è libera), ultima finisce alle 13:20
        assertNull(liveDay(schedule, emptyMap(), settings, at(9, 0)))
        assertNull(liveDay(schedule, emptyMap(), settings, at(13, 20)))
    }

    @Test
    fun lessonInProgressLikeTheMac() {
        val live = liveDay(schedule, emptyMap(), settings, at(10, 30))!!
        assertEquals("5D fino 11:20", live.pill)
        assertEquals("5D · fino alle 11:20", live.title)
        assertEquals("2.03 · Bonelli", live.text)
        assertTrue(live.lines.single { it.current }.text.startsWith("10:20  5D"))
    }

    @Test
    fun recessAndFreeHourShowTheNextLesson() {
        val recess = liveDay(schedule, emptyMap(), settings, at(11, 30))!!
        assertEquals("1E alle 12:30", recess.pill)
        assertEquals("Intervallo fino alle 11:40", recess.text)
        val free = liveDay(schedule, emptyMap(), settings, at(12, 0))!!
        assertEquals("1E alle 12:30", free.pill)
        assertEquals("Ora libera", free.text)
    }

    @Test
    fun listStartsFromTheCurrentHourAndMarksSubstitutions() {
        val sub = mapOf(OverrideKey(monday, 3) to Override.of(monday, 3, Lesson("2A")))
        val lines = liveDay(schedule, sub, settings, at(10, 30))!!.lines.map { it.text }
        assertEquals(
            listOf("10:20  5D · 2.03", "☕  11:20–11:40", "11:40  2A · supplenza", "12:30  1E"),
            lines,
        )
    }

    @Test
    fun longListEndsWithTheEndOfTheDay() {
        // 5 righe restanti: la tendina ne mostra 4, quindi le ultime 2 ore finiscono nella riga di fine
        val lines = liveDay(schedule, emptyMap(), settings, at(10, 0))!!.lines
        assertEquals(
            listOf("9:20  3B · Lab 2", "10:20  5D · 2.03", "☕  11:20–11:40", "+ 2 ore · Fine 13:20"),
            lines.map { it.text },
        )
        assertTrue(lines.first().current)
    }

    @Test
    fun lastHourIsListedWhenItFits() {
        val lines = liveDay(schedule, emptyMap(), settings, at(12, 40))!!.lines
        assertEquals(listOf("12:30  1E"), lines.map { it.text })
    }

    @Test
    fun pausedDaysShowNothingAndAreSkipped() {
        val paused = settings.copy(pauseFrom = monday, pauseTo = monday)
        assertNull(liveDay(schedule, emptyMap(), paused, at(10, 30)))
        assertEquals(at(9, 20, monday.plusWeeks(1)), nextLiveDayChange(schedule, emptyMap(), paused, at(8, 0)))
    }

    @Test
    fun nextChangeIsTheNextBoundary() {
        assertEquals(at(9, 20), nextLiveDayChange(schedule, emptyMap(), settings, at(7, 0)))
        assertEquals(at(11, 20), nextLiveDayChange(schedule, emptyMap(), settings, at(10, 30)))
        assertEquals(at(9, 20, monday.plusWeeks(1)), nextLiveDayChange(schedule, emptyMap(), settings, at(13, 20)))
    }
}
