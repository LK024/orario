package io.github.lk024.orario.data

/**
 * Legge il CSV pubblicato dal foglio Google. Il foglio ha due blocchi, ognuno con
 * un'intestazione che inizia con "Giorno":
 *
 * 1. le lezioni: una riga per giorno, una colonna per ora, celle "Classe | Aula | Docente";
 * 2. gli orari: ore totali, pranzo, durata di ogni ora e, facoltative, le colonne
 *    "Inizio", "Durata intervallo" e "Intervallo dopo ora".
 */
object ScheduleParser {

    fun parse(csv: String): Schedule {
        val rows = parseCsv(csv)
            .map { row -> row.map(String::trim) }
            .filter { row -> row.any(String::isNotEmpty) }
        require(rows.isNotEmpty()) { "CSV vuoto" }

        val configHeaderIndex = (1 until rows.size).firstOrNull { i ->
            rows[i].firstOrNull()?.normalized() == "giorno" && rows[i].size > 2
        } ?: rows.size

        val lessonHeader = rows.first()
        val hourCount = lessonHeader.drop(1).indexOfLast(String::isNotEmpty) + 1
        val lessonRows = rows.subList(1, configHeaderIndex).filter { it[0].isNotEmpty() }

        val configs = if (configHeaderIndex < rows.size) {
            parseConfigs(rows[configHeaderIndex], rows.subList(configHeaderIndex + 1, rows.size))
        } else {
            emptyList()
        }

        val days = lessonRows.mapIndexed { index, row ->
            val name = row[0]
            val config = configs.firstOrNull { it.first.normalized() == name.normalized() }?.second
                ?: configs.getOrNull(index)?.second
                ?: DayConfig.Fallback
            Day(
                name = name,
                lessons = (1..maxOf(hourCount, config.totalHours)).map { parseLesson(row.getOrNull(it)) },
                config = config,
            )
        }
        require(days.isNotEmpty()) { "Nessun giorno trovato nel CSV" }
        return Schedule(days)
    }

    private fun parseLesson(cell: String?): Lesson? {
        val parts = cell.orEmpty().split('|').map(String::trim)
        val title = parts[0].ifEmpty { return null }
        return Lesson(
            title = title,
            room = parts.getOrNull(1)?.ifEmpty { null },
            teacher = parts.getOrNull(2)?.ifEmpty { null },
        )
    }

    private fun parseConfigs(header: List<String>, rows: List<List<String>>): List<Pair<String, DayConfig>> {
        val columns = header.map(String::normalized)
        fun column(fallback: Int?, predicate: (String) -> Boolean) =
            columns.indexOfFirst(predicate).takeIf { it >= 0 } ?: fallback

        val total = column(1) { it.startsWith("ore totali") }
        val lunchDuration = column(2) { it.startsWith("durata pranzo") }
        val lunchAfter = column(3) { it.startsWith("ora pranzo") }
        val start = column(null) { it.startsWith("inizio") || it.startsWith("ora inizio") }
        val breakDuration = column(null) { it.startsWith("durata intervallo") }
        val breakAfter = column(null) { it.startsWith("intervallo dopo") || it.startsWith("ora intervallo") }
        val durationRegex = Regex("""^durata (\d+)""")
        val durationColumns = columns.withIndex()
            .mapNotNull { (i, name) -> durationRegex.find(name)?.groupValues?.get(1)?.toInt()?.let { it to i } }
            .sortedBy { it.first }
            .map { it.second }
            .ifEmpty { (4..12).toList() }

        return rows.filter { it[0].isNotEmpty() }.map { row ->
            fun int(index: Int?) = index?.let { row.getOrNull(it) }?.toDoubleOrNull()?.toInt()
            val defaults = DayConfig.Fallback
            row[0] to DayConfig(
                totalHours = int(total) ?: defaults.totalHours,
                durations = durationColumns.map { int(it) ?: 0 },
                lunchDuration = int(lunchDuration) ?: 0,
                lunchAfter = int(lunchAfter) ?: 0,
                start = start?.let { row.getOrNull(it) }?.let(::parseTime) ?: defaults.start,
                breakDuration = int(breakDuration) ?: defaults.breakDuration,
                breakAfter = int(breakAfter) ?: defaults.breakAfter,
            )
        }
    }

    /** Accetta "8:20", "08:20:00" e "8.20". */
    private fun parseTime(value: String): Int? {
        val match = Regex("""^(\d{1,2})[:.](\d{2})""").find(value) ?: return null
        val (h, m) = match.destructured
        return h.toInt() * 60 + m.toInt()
    }

    /** CSV RFC 4180: virgolette, virgolette raddoppiate e a capo dentro le celle. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> cell.append(c)
                c == ',' -> { row += cell.toString(); cell.clear() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear()
                    rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}
