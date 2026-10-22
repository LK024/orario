import Foundation

/// Data senza ora, nel formato del foglio e dell'app Android ("2026-10-22").
public struct LocalDate: Codable, Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "it_IT")
        return calendar
    }()

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    public init(_ date: Date) {
        let parts = Self.calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year!, month: parts.month!, day: parts.day!)
    }

    public init?(iso: String) {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    public static var today: LocalDate { LocalDate(Date()) }

    public var description: String { String(format: "%04d-%02d-%02d", year, month, day) }

    public var date: Date { Self.calendar.date(from: DateComponents(year: year, month: month, day: day))! }

    /// 1 = lunedì … 7 = domenica.
    public var weekday: Int { (Self.calendar.component(.weekday, from: date) + 5) % 7 + 1 }

    public func adding(days: Int) -> LocalDate { LocalDate(Self.calendar.date(byAdding: .day, value: days, to: date)!) }

    /// Il lunedì della settimana da mostrare: nel weekend è già quella successiva.
    public var displayedWeekStart: LocalDate {
        weekday >= 6 ? adding(days: 8 - weekday) : adding(days: 1 - weekday)
    }

    public static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    public init(from decoder: Decoder) throws {
        let text = try decoder.singleValueContainer().decode(String.self)
        guard let date = LocalDate(iso: text) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Data non valida: \(text)"))
        }
        self = date
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

public struct OverrideKey: Hashable, Codable, Sendable {
    public let date: LocalDate
    public let hour: Int

    public init(date: LocalDate, hour: Int) {
        self.date = date
        self.hour = hour
    }
}

/// Variazione valida solo per una data: supplenza (`lesson` presente) o ora resa libera (`lesson` nil).
public struct Override: Codable, Hashable, Sendable {
    public let date: LocalDate
    public let hour: Int
    public let lesson: Lesson?

    public init(date: LocalDate, hour: Int, lesson: Lesson?) {
        self.date = date
        self.hour = hour
        self.lesson = lesson
    }

    public var key: OverrideKey { OverrideKey(date: date, hour: hour) }
}

/// Modifica fatta su questo dispositivo e non ancora arrivata nel foglio.
public struct PendingChange: Codable, Hashable, Sendable {
    public let date: LocalDate
    public let hour: Int
    public let lesson: Lesson?
    public let delete: Bool

    public init(date: LocalDate, hour: Int, lesson: Lesson? = nil, delete: Bool = false) {
        self.date = date
        self.hour = hour
        self.lesson = lesson
        self.delete = delete
    }

    public var key: OverrideKey { OverrideKey(date: date, hour: hour) }
}

extension Day {
    /// Il giorno con le variazioni di `date` applicate.
    public func forDate(_ date: LocalDate, overrides: [OverrideKey: Override]) -> Day {
        guard overrides.keys.contains(where: { $0.date == date }) else { return self }
        var copy = self
        copy.lessons = (0..<max(lessons.count, config.totalHours)).map { hour in
            if let change = overrides[OverrideKey(date: date, hour: hour)] { return change.lesson }
            return lesson(at: hour)
        }
        return copy
    }

    /// Data di questo giorno nella settimana che inizia con `weekStart`.
    public func date(inWeekStarting weekStart: LocalDate, index: Int) -> LocalDate {
        weekStart.adding(days: (weekday ?? index + 1) - 1)
    }
}

/// Le modifiche non ancora inviate vincono su quello che c'è nel foglio.
public func mergeWithPending(remote: [Override], pending: [PendingChange]) -> [OverrideKey: Override] {
    var merged = Dictionary(remote.map { ($0.key, $0) }, uniquingKeysWith: { _, last in last })
    for change in pending {
        if change.delete {
            merged[change.key] = nil
        } else {
            merged[change.key] = Override(date: change.date, hour: change.hour, lesson: change.lesson)
        }
    }
    return merged
}
