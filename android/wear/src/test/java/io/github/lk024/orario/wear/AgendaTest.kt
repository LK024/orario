package io.github.lk024.orario.wear

import io.github.lk024.orario.data.BreakKind
import io.github.lk024.orario.data.Day
import io.github.lk024.orario.data.DayConfig
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.Override
import io.github.lk024.orario.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AgendaTest {

    // Giovedì e venerdì come nel foglio vero
    private val schedule = Schedule(listOf(
        Day("Giovedì", listOf(null, Lesson("1E", "Lab 1"), null, Lesson("5D", "2.03"), null, null), DayConfig.Fallback),
        Day(
            "Venerdì",
            listOf(null, null, null, null, Lesson("5D"), Lesson("3D"), Lesson("5E"), null, Lesson("1E")),
            DayConfig(9, listOf(60, 50, 50, 50, 50, 50, 50, 50, 50), lunchDuration = 35, lunchAfter = 6),
        ),
    ))
    private val thursday = LocalDate.of(2026, 10, 22)

    private fun at(date: LocalDate, h: Int, m: Int) = LocalDateTime.of(date, java.time.LocalTime.of(h, m))

    @Test
    fun lessonInProgress() {
        val status = schedule.statusAt(at(thursday, 9, 45), emptyMap()) as Status.InLesson
        assertEquals("1E", status.lesson.title)
        assertEquals(10 * 60 + 20, status.slot.end)
    }

    @Test
    fun recessShowsTheNextLesson() {
        val status = schedule.statusAt(at(thursday, 11, 30), emptyMap()) as Status.InPause
        assertEquals(BreakKind.Recess, status.pause.kind)
        assertEquals("5D", status.next.lesson!!.title)
    }

    @Test
    fun freeHourShowsTheNextLessonToday() {
        val status = schedule.statusAt(at(thursday, 8, 30), emptyMap()) as Status.Next
        assertEquals("1E", status.lesson.title)
        assertTrue(status.today)
    }

    @Test
    fun afterTheLastLessonShowsTomorrow() {
        val status = schedule.statusAt(at(thursday, 13, 0), emptyMap()) as Status.Next
        assertEquals("5D", status.lesson.title)
        assertEquals(thursday.plusDays(1), status.date)
    }

    @Test
    fun weekendJumpsToThursday() {
        val status = schedule.statusAt(at(thursday.plusDays(1), 17, 0), emptyMap()) as Status.Next
        assertEquals(thursday.plusWeeks(1), status.date)
    }

    @Test
    fun substitutionsAreApplied() {
        val sub = listOf(Override.of(thursday, 0, Lesson("2A"))).associateBy { it.key }
        val status = schedule.statusAt(at(thursday, 8, 30), sub) as Status.InLesson
        assertEquals("2A", status.lesson.title)
    }

    @Test
    fun changesAreEveryBoundaryAfterNow() {
        val changes = schedule.changesToday(at(thursday, 11, 30), emptyMap()).map { it.toLocalTime().toString() }
        assertEquals(listOf("11:40", "12:30", "13:20", "14:10"), changes)
    }

    private val friday = thursday.plusDays(1)

    @Test
    fun tileShowsTodayUntilTheLastLesson() {
        val state = schedule.tileState(at(thursday, 7, 30), emptyMap()) as TileState.Lessons
        assertTrue(state.live)
        assertEquals(null, state.title)
        assertEquals(thursday, state.agenda.date)
    }

    @Test
    fun restForAnHourThenTomorrow() {
        // Giovedì l'ultima lezione finisce alle 12:30
        val rest = schedule.tileState(at(thursday, 13, 0), emptyMap()) as TileState.Rest
        assertEquals("Sei libero", rest.headline)
        assertEquals("Domani alle 12:10 · 5D", rest.nextLabel)
        val tomorrow = schedule.tileState(at(thursday, 13, 30), emptyMap()) as TileState.Lessons
        assertEquals("Domani", tomorrow.title)
        assertEquals(friday, tomorrow.agenda.date)
    }

    @Test
    fun weekendRestUntilTheEveningBefore() {
        val rest = schedule.tileState(at(friday, 18, 0), emptyMap()) as TileState.Rest
        assertEquals("Buon weekend", rest.headline)
        // Prossimo giorno di scuola: giovedì della settimana dopo (nel test non ci sono altri giorni)
        val wednesdayEvening = schedule.tileState(at(thursday.plusDays(6), 18, 0), emptyMap()) as TileState.Lessons
        assertEquals(thursday.plusWeeks(1), wednesdayEvening.agenda.date)
        assertEquals("Domani", wednesdayEvening.title)
    }

    @Test
    fun tileChangesIncludeRestEndAndEvenings() {
        val changes = schedule.tileChanges(at(thursday, 12, 0), emptyMap()).map { it.toString() }
        assertTrue("2026-10-22T12:30" in changes)
        assertTrue("2026-10-22T13:30" in changes)
        assertTrue("2026-10-22T18:00" in changes)
    }
}
