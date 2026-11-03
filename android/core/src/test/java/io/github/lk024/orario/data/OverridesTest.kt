package io.github.lk024.orario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class OverridesTest {

    private val day = Day(
        name = "Giovedì",
        lessons = listOf(null, Lesson("1E", "Lab 1"), null, Lesson("5D", "2.03"), null, null),
        config = DayConfig.Fallback,
    )
    private val thursday = LocalDate.of(2026, 10, 22)

    @Test
    fun substitutionReplacesOnlyThatDate() {
        val overrides = listOf(Override.of(thursday, 2, Lesson("2A", "1.05"))).associateBy { it.key }

        assertEquals(Lesson("2A", "1.05"), day.forDate(thursday, overrides).lessons[2])
        assertNull(day.forDate(thursday.plusWeeks(1), overrides).lessons[2])
    }

    @Test
    fun freeHourRemovesTheLesson() {
        val overrides = listOf(Override.of(thursday, 1, null)).associateBy { it.key }
        val lessons = day.forDate(thursday, overrides).lessons

        assertNull(lessons[1])
        assertEquals(Lesson("5D", "2.03"), lessons[3])
    }

    @Test
    fun noOverridesKeepsTheSameDay() {
        assertSame(day, day.forDate(thursday, emptyMap()))
    }
}
