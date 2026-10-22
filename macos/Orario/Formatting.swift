import Foundation
import OrarioCore

private let italian = Locale(identifier: "it_IT")

private func formatter(_ template: String) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = italian
    formatter.setLocalizedDateFormatFromTemplate(template)
    return formatter
}

private let dayMonth = formatter("d MMMM")
private let weekdayDayMonth = formatter("EEEE d MMMM")
private let time = formatter("H:mm")

extension LocalDate {
    var dayMonthText: String { dayMonth.string(from: date) }
    var fullText: String { weekdayDayMonth.string(from: date) }
}

func timeText(_ date: Date) -> String { time.string(from: date) }

/// "19–23 ottobre" oppure "28 ottobre – 1 novembre".
func weekRangeText(from start: LocalDate, to end: LocalDate) -> String {
    start.month == end.month ? "\(start.day)–\(end.dayMonthText)" : "\(start.dayMonthText) – \(end.dayMonthText)"
}
