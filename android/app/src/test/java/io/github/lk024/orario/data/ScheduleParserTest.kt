package io.github.lk024.orario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class ScheduleParserTest {

    // Copia del CSV pubblicato dal foglio reale
    private val sheetCsv = """
        Giorno,1ª ora,2ª ora,3ª ora,4ª ora,5ª ora,6ª ora,7ª ora,8ª ora,9ª ora,,,
        Lunedì,3B | Lab 2,5D | Lab Rob | Bonelli,,1E | Lab Ele,3E | N2.03,,,,,,,
        Martedì,3D | Lab 4 | Bonavoglia,3B | Lab Rob,,,3E | Lab 2,5E | Lab 2,,,,,,
        Mercoledì,,,5E | 2.05,1E | Lab 2 | Saporito,3E | Lab 4 | Saporito,3D | N2.02,,,,,,
        Giovedì,,1E | Lab 1 | Saporito,,5D | 2.03,,,,,,,,
        Venerdì,,,,,5D | 2.03,3D | Lab Ele,5E | Lab 3 | Bonelli,,1E | Lab 2,,,
        ,,,,,,,,,,,,
        ,,,,,,,,,,,,
        Giorno,Ore totali,Durata pranzo,Ora pranzo,Durata 1ª ora,Durata 2ª ora,Durata 3ª ora,Durata 4ª ora,Durata 5ª ora,Durata 6ª ora,Durata 7ª ora,Durata 8ª ora,Durata 9ª ora
        Lunedì,6,0,0,60,60,60,50,50,50,,,
        Martedì,6,0,0,60,60,60,50,50,50,,,
        Mercoledì,6,0,0,60,60,60,50,50,50,,,
        Giovedì,6,0,0,60,60,60,50,50,50,,,
        Venerdì,9,35,6,60,50,50,50,50,50,50,50,50
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun parsesLessonsAndConfigs() {
        val schedule = ScheduleParser.parse(sheetCsv)
        assertEquals(listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì"), schedule.days.map { it.name })

        val monday = schedule.days[0]
        assertEquals(Lesson("3B", "Lab 2"), monday.lessons[0])
        assertEquals(Lesson("5D", "Lab Rob", "Bonelli"), monday.lessons[1])
        assertNull(monday.lessons[2])
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)

        val friday = schedule.days[4]
        assertEquals(9, friday.config.totalHours)
        assertEquals(35, friday.config.lunchDuration)
        assertEquals(6, friday.config.lunchAfter)
        assertEquals(Lesson("1E", "Lab 2"), friday.lessons[8])
        assertEquals(DayConfig.DEFAULT_START, friday.config.start)
    }

    @Test
    fun timelineMatchesThePwa() {
        val friday = ScheduleParser.parse(sheetCsv).days[4]
        val slots = friday.timeline().map { slot ->
            val label = when (slot) {
                is LessonSlot -> "${slot.hour + 1}"
                is BreakSlot -> slot.kind.label
            }
            "$label ${formatMinutes(slot.start)}-${formatMinutes(slot.end)}"
        }
        assertEquals(
            listOf(
                "1 8:20-9:20", "2 9:20-10:10", "3 10:10-11:00", "Intervallo 11:00-11:20",
                "4 11:20-12:10", "5 12:10-13:00", "6 13:00-13:50", "Pranzo 13:50-14:25",
                "7 14:25-15:15", "8 15:15-16:05", "9 16:05-16:55",
            ),
            slots,
        )
    }

    @Test
    fun readsOptionalStartAndBreakColumns() {
        val csv = """
            Giorno,1ª ora,2ª ora
            Lunedì,3B,5D
            Giorno,Ore totali,Durata pranzo,Ora pranzo,Durata 1ª ora,Durata 2ª ora,Inizio,Durata intervallo,Intervallo dopo ora
            Lunedì,2,0,0,60,60,8:00:00,15,1
        """.trimIndent()
        val config = ScheduleParser.parse(csv).days[0].config
        assertEquals(8 * 60, config.start)
        assertEquals(15, config.breakDuration)
        assertEquals(1, config.breakAfter)
        assertEquals(listOf(60, 60), config.durations)
    }

    @Test
    fun handlesQuotedCells() {
        val rows = ScheduleParser.parseCsv("a,\"b, c\",\"d \"\"e\"\"\"\nf,\"g\nh\"")
        assertEquals(listOf(listOf("a", "b, c", "d \"e\""), listOf("f", "g\nh")), rows)
    }

    @Test
    fun missingConfigFallsBackToDefaults() {
        val day = ScheduleParser.parse("Giorno,1ª ora\nLunedì,3B").days[0]
        assertEquals(DayConfig.Fallback, day.config)
    }
}
