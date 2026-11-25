package io.github.lk024.orario.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRangeLabelTest {

    @Test
    fun sameMonth() {
        assertEquals("16–20 nov", weekRangeLabel(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun acrossMonths() {
        assertEquals("30 nov – 4 dic", weekRangeLabel(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 4)))
    }
}
