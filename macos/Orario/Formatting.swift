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
private let monthOnly = formatter("LLLL")

extension LocalDate {
    var dayMonthText: String { dayMonth.string(from: date) }
    var fullText: String { weekdayDayMonth.string(from: date) }
    /// "Novembre", con l'iniziale maiuscola come nei titoli di Calendario.
    var monthName: String { monthOnly.string(from: date).capitalized(with: italian) }
}

func timeText(_ date: Date) -> String { time.string(from: date) }
