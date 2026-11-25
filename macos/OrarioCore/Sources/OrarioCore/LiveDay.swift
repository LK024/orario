import Foundation

/// Pausa di promemoria, Live Update e icona del Mac, condivisa fra le app tramite il foglio.
public struct PausePeriod: Codable, Hashable, Sendable {
    public let from: LocalDate
    public let to: LocalDate

    public init(from: LocalDate, to: LocalDate) {
        self.from = from
        self.to = to
    }

    public func contains(_ date: LocalDate) -> Bool { date >= from && date <= to }
}

extension Schedule {
    /// La giornata di `date` con supplenze e ore libere applicate, se è un giorno di scuola.
    func effectiveDay(on date: LocalDate, overrides: [OverrideKey: Override]) -> Day? {
        days.first { $0.weekday == date.weekday }?.forDate(date, overrides: overrides)
    }

    /// Dall'inizio della prima lezione alla fine dell'ultima di `date`, in minuti: come il Live Update
    /// di Android. Nil nei giorni senza lezioni o in pausa.
    public func liveSpan(on date: LocalDate, overrides: [OverrideKey: Override], pause: PausePeriod?) -> Range<Int>? {
        if pause?.contains(date) == true { return nil }
        guard let lessons = effectiveDay(on: date, overrides: overrides)?.lessonSlots().filter({ $0.lesson != nil }),
              let first = lessons.first, let last = lessons.last else { return nil }
        return first.start..<last.end
    }

    /// Il prossimo minuto di `date` dopo `minute` in cui cambia qualcosa (inizio o fine di un'ora o di
    /// una pausa); nil se fino a mezzanotte non cambia più niente.
    public func nextChange(on date: LocalDate, after minute: Int, overrides: [OverrideKey: Override]) -> Int? {
        guard let day = effectiveDay(on: date, overrides: overrides) else { return nil }
        return day.timeline().flatMap { [$0.start, $0.end] }.filter { $0 > minute }.min()
    }
}

/// Stato della pausa nel foglio, come lo restituisce lo script.
public enum RemotePause: Equatable, Sendable {
    /// Script non ancora aggiornato: la risposta non parla di pausa, quindi resta tutto com'è.
    case unsupported
    case period(PausePeriod?)

    /// Legge la chiave "pause" della risposta: assente, null oppure `{from, to}` in formato ISO.
    public static func parse(_ data: Data) throws -> RemotePause {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return .unsupported }
        if let error = object["error"] as? String { throw SubstitutionsAPI.APIError.script(error) }
        guard let value = object["pause"] else { return .unsupported }
        guard let pause = value as? [String: Any],
              let from = (pause["from"] as? String).flatMap(LocalDate.init(iso:)),
              let to = (pause["to"] as? String).flatMap(LocalDate.init(iso:)) else { return .period(nil) }
        return .period(PausePeriod(from: from, to: to))
    }
}
