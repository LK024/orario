import Foundation

public struct Lesson: Codable, Hashable, Sendable {
    public var title: String
    public var room: String?
    public var teacher: String?

    public init(title: String, room: String? = nil, teacher: String? = nil) {
        self.title = title
        self.room = room
        self.teacher = teacher
    }
}

/// Orari di un giorno. Tutti i tempi sono in minuti, `start` è in minuti dalla mezzanotte.
public struct DayConfig: Codable, Hashable, Sendable {
    public var totalHours: Int
    public var durations: [Int]
    public var lunchDuration: Int
    public var lunchAfter: Int
    public var start: Int
    public var breakDuration: Int
    public var breakAfter: Int

    public static let defaultStart = 8 * 60 + 20
    public static let defaultBreakDuration = 20
    public static let defaultBreakAfter = 3

    /// Stessi valori di fallback della PWA, usati se il foglio non ha la riga del giorno.
    public static let fallback = DayConfig(totalHours: 6, durations: [60, 60, 60, 50, 50, 50])

    public init(
        totalHours: Int,
        durations: [Int],
        lunchDuration: Int = 0,
        lunchAfter: Int = 0,
        start: Int = defaultStart,
        breakDuration: Int = defaultBreakDuration,
        breakAfter: Int = defaultBreakAfter
    ) {
        self.totalHours = totalHours
        self.durations = durations
        self.lunchDuration = lunchDuration
        self.lunchAfter = lunchAfter
        self.start = start
        self.breakDuration = breakDuration
        self.breakAfter = breakAfter
    }
}

public struct Day: Codable, Hashable, Sendable {
    public var name: String
    public var lessons: [Lesson?]
    public var config: DayConfig

    public init(name: String, lessons: [Lesson?], config: DayConfig) {
        self.name = name
        self.lessons = lessons
        self.config = config
    }

    /// Giorno della settimana ISO (1 = lunedì … 7 = domenica), dedotto dal nome.
    public var weekday: Int? { weekdayFromName(name) }

    public func lesson(at hour: Int) -> Lesson? {
        lessons.indices.contains(hour) ? lessons[hour] : nil
    }
}

public struct Schedule: Codable, Hashable, Sendable {
    public var days: [Day]

    public init(days: [Day]) {
        self.days = days
    }
}

public enum BreakKind: String, Codable, Hashable, Sendable {
    case recess, lunch

    public var label: String { self == .recess ? "Intervallo" : "Pranzo" }
}

public struct Slot: Hashable, Sendable {
    public enum Kind: Hashable, Sendable {
        case lesson(hour: Int, lesson: Lesson?)
        case pause(BreakKind)
    }

    public var kind: Kind
    public var start: Int
    public var end: Int

    public func contains(minute: Int) -> Bool { minute >= start && minute < end }
}

extension Day {
    /// Sequenza di ore e pause del giorno, con orari calcolati come nella PWA.
    public func timeline() -> [Slot] {
        var slots: [Slot] = []
        var t = config.start
        for hour in 0..<config.totalHours {
            let duration = config.durations.indices.contains(hour) ? config.durations[hour] : 0
            slots.append(Slot(kind: .lesson(hour: hour, lesson: lesson(at: hour)), start: t, end: t + duration))
            t += duration
            if hour == config.totalHours - 1 { break }
            if config.breakAfter > 0, hour == config.breakAfter - 1, config.breakDuration > 0 {
                slots.append(Slot(kind: .pause(.recess), start: t, end: t + config.breakDuration))
                t += config.breakDuration
            }
            if config.lunchAfter > 0, hour == config.lunchAfter - 1, config.lunchDuration > 0 {
                slots.append(Slot(kind: .pause(.lunch), start: t, end: t + config.lunchDuration))
                t += config.lunchDuration
            }
        }
        return slots
    }

    /// Solo le ore di lezione (libere comprese), con i loro orari.
    public func lessonSlots() -> [(hour: Int, lesson: Lesson?, start: Int, end: Int)] {
        timeline().compactMap { slot in
            if case let .lesson(hour, lesson) = slot.kind { return (hour, lesson, slot.start, slot.end) }
            return nil
        }
    }
}

public func formatMinutes(_ minutes: Int) -> String {
    String(format: "%d:%02d", minutes / 60, minutes % 60)
}

func normalized(_ text: String) -> String {
    text.trimmingCharacters(in: .whitespaces)
        .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "it_IT"))
}

private let dayNames = ["lunedi": 1, "martedi": 2, "mercoledi": 3, "giovedi": 4, "venerdi": 5, "sabato": 6, "domenica": 7]

func weekdayFromName(_ name: String) -> Int? {
    let n = normalized(name)
    if let day = dayNames[n] { return day }
    guard n.count >= 3 else { return nil }
    return dayNames.first { $0.key.hasPrefix(n) }?.value
}
