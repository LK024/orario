import Foundation

/// Legge il CSV pubblicato dal foglio Google, con la stessa logica dell'app Android:
/// blocco delle lezioni, poi blocco degli orari con le colonne riconosciute dal nome.
public enum ScheduleParser {

    public enum ParseError: Error { case empty, noDays }

    public static func parse(_ csv: String) throws -> Schedule {
        let rows = parseCSV(csv)
            .map { $0.map { $0.trimmingCharacters(in: .whitespaces) } }
            .filter { $0.contains { !$0.isEmpty } }
        guard let header = rows.first else { throw ParseError.empty }

        let configHeaderIndex = rows.indices.dropFirst().first {
            normalized(rows[$0].first ?? "") == "giorno" && rows[$0].count > 2
        } ?? rows.count

        let hourCount = (header.dropFirst().lastIndex { !$0.isEmpty } ?? 0)
        let lessonRows = rows[1..<configHeaderIndex].filter { !($0.first ?? "").isEmpty }
        let configs = configHeaderIndex < rows.count
            ? parseConfigs(header: rows[configHeaderIndex], rows: Array(rows[(configHeaderIndex + 1)...]))
            : []

        let days = lessonRows.enumerated().map { index, row -> Day in
            let name = row[0]
            let config = configs.first { normalized($0.name) == normalized(name) }?.config
                ?? (configs.indices.contains(index) ? configs[index].config : nil)
                ?? .fallback
            let count = max(hourCount, config.totalHours)
            let lessons = (0..<count).map { parseLesson(row.indices.contains($0 + 1) ? row[$0 + 1] : "") }
            return Day(name: name, lessons: lessons, config: config)
        }
        guard !days.isEmpty else { throw ParseError.noDays }
        return Schedule(days: days)
    }

    static func parseLesson(_ cell: String) -> Lesson? {
        let parts = cell.split(separator: "|", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespaces) }
        guard let title = parts.first, !title.isEmpty else { return nil }
        func part(_ i: Int) -> String? { parts.indices.contains(i) && !parts[i].isEmpty ? parts[i] : nil }
        return Lesson(title: title, room: part(1), teacher: part(2))
    }

    private static func parseConfigs(header: [String], rows: [[String]]) -> [(name: String, config: DayConfig)] {
        let columns = header.map(normalized)
        func column(_ fallback: Int?, _ prefixes: String...) -> Int? {
            columns.firstIndex { name in prefixes.contains { name.hasPrefix($0) } } ?? fallback
        }
        let total = column(1, "ore totali")
        let lunchDuration = column(2, "durata pranzo")
        let lunchAfter = column(3, "ora pranzo")
        let start = column(nil, "inizio", "ora inizio")
        let breakDuration = column(nil, "durata intervallo")
        let breakAfter = column(nil, "intervallo dopo", "ora intervallo")

        var durationColumns = columns.enumerated().compactMap { index, name -> (Int, Int)? in
            guard name.hasPrefix("durata "),
                  let number = Int(name.dropFirst(7).prefix { $0.isNumber }) else { return nil }
            return (number, index)
        }
        .sorted { $0.0 < $1.0 }
        .map(\.1)
        if durationColumns.isEmpty { durationColumns = Array(4...12) }

        return rows.filter { !($0.first ?? "").isEmpty }.map { row in
            func cell(_ i: Int?) -> String? { i.flatMap { row.indices.contains($0) ? row[$0] : nil } }
            func int(_ i: Int?) -> Int? { cell(i).flatMap(Double.init).map { Int($0) } }
            let fallback = DayConfig.fallback
            return (row[0], DayConfig(
                totalHours: int(total) ?? fallback.totalHours,
                durations: durationColumns.map { int($0) ?? 0 },
                lunchDuration: int(lunchDuration) ?? 0,
                lunchAfter: int(lunchAfter) ?? 0,
                start: cell(start).flatMap(parseTime) ?? fallback.start,
                breakDuration: int(breakDuration) ?? fallback.breakDuration,
                breakAfter: int(breakAfter) ?? fallback.breakAfter
            ))
        }
    }

    /// Accetta "8:20", "08:20:00" e "8.20.00".
    static func parseTime(_ value: String) -> Int? {
        let pieces = value.split(whereSeparator: { $0 == ":" || $0 == "." })
        guard pieces.count >= 2, let h = Int(pieces[0]), let m = Int(pieces[1]), pieces[1].count == 2 else { return nil }
        return h * 60 + m
    }

    /// CSV RFC 4180: virgolette, virgolette raddoppiate e a capo dentro le celle.
    static func parseCSV(_ text: String) -> [[String]] {
        var rows: [[String]] = []
        var row: [String] = []
        var cell = ""
        var quoted = false
        var chars = Array(text).makeIterator()
        var pending: Character?

        while let c = pending ?? chars.next() {
            pending = nil
            if quoted {
                if c == "\"" {
                    let next = chars.next()
                    if next == "\"" { cell.append("\"") } else { quoted = false; pending = next }
                } else {
                    cell.append(c)
                }
            } else if c == "\"" {
                quoted = true
            } else if c == "," {
                row.append(cell); cell = ""
            } else if c == "\n" || c == "\r" || c == "\r\n" {
                row.append(cell); cell = ""
                rows.append(row); row = []
            } else {
                cell.append(c)
            }
        }
        if !cell.isEmpty || !row.isEmpty {
            row.append(cell)
            rows.append(row)
        }
        return rows
    }
}
