import AppKit
import Observation
import OrarioCore

/// Stato dell'app: orario e supplenze salvati su disco, aggiornati dal foglio in background.
/// L'interfaccia mostra sempre i dati salvati, quindi si apre subito e non "lampeggia".
@Observable
final class AppModel {
    private(set) var schedule: Schedule?
    private(set) var overrides: [OverrideKey: Override] = [:]
    private(set) var lastSync: Date?
    private(set) var isSyncing = false
    private(set) var now = Date()
    var errorMessage: String?

    private var pending: [PendingChange] = []
    private let scheduleSource = ScheduleSource(csvURL: Config.sheetCSVURL)
    private let api = SubstitutionsAPI(url: Config.syncURL, token: Config.syncToken)
    @ObservationIgnored private var clock: Timer?

    private struct Stored: Codable {
        var schedule: Schedule?
        var overrides: [Override]
        var pending: [PendingChange]
        var lastSync: Date?
    }

    private let storeURL: URL = {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Orario", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("state.json")
    }()

    init() {
        load()
        startClock()
        // Dopo lo stop del Mac l'ora e l'evidenziazione vanno ricalcolate subito
        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didWakeNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick(refreshIfStale: true) }
        }
        Task { await refresh() }
    }

    // MARK: - Tempo

    var today: LocalDate { LocalDate(now) }
    var minuteOfDay: Int { Calendar.current.component(.hour, from: now) * 60 + Calendar.current.component(.minute, from: now) }
    var weekStart: LocalDate { today.displayedWeekStart }

    private func startClock() {
        // Scatta all'inizio di ogni minuto
        let next = Calendar.current.nextDate(after: Date(), matching: DateComponents(second: 0), matchingPolicy: .nextTime)!
        let timer = Timer(fire: next, interval: 60, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick(refreshIfStale: true) }
        }
        RunLoop.main.add(timer, forMode: .common)
        clock = timer
    }

    private func tick(refreshIfStale: Bool) {
        now = Date()
        if refreshIfStale, !isSyncing, Date().timeIntervalSince(lastSync ?? .distantPast) > 15 * 60 {
            Task { await refresh() }
        }
    }

    // MARK: - Dati per l'interfaccia

    var days: [Day] { schedule?.days ?? [] }

    func date(ofDayAt index: Int) -> LocalDate { days[index].date(inWeekStarting: weekStart, index: index) }

    /// Il giorno con supplenze e ore libere già applicate.
    func effectiveDay(at index: Int) -> Day { days[index].forDate(date(ofDayAt: index), overrides: overrides) }

    func override(on date: LocalDate, hour: Int) -> Override? { overrides[OverrideKey(date: date, hour: hour)] }

    /// Classi, aule e docenti già presenti nell'orario, per i suggerimenti.
    var suggestions: (titles: [String], rooms: [String], teachers: [String]) {
        let lessons = days.flatMap(\.lessons).compactMap { $0 }
        return (Array(Set(lessons.map(\.title))).sorted(),
                Array(Set(lessons.compactMap(\.room))).sorted(),
                Array(Set(lessons.compactMap(\.teacher))).sorted())
    }

    /// Le lezioni di oggi (o del prossimo giorno di scuola) per la barra dei menu.
    struct DayPlan {
        let date: LocalDate
        let day: Day
        let slots: [Slot]
    }

    var upcomingDay: DayPlan? {
        guard let schedule else { return nil }
        for offset in 0..<8 {
            let date = today.adding(days: offset)
            guard let day = schedule.days.first(where: { $0.weekday == date.weekday }) else { continue }
            let effective = day.forDate(date, overrides: overrides)
            let lessons = effective.lessonSlots().filter { $0.lesson != nil }
            guard let first = lessons.first, let last = lessons.last else { continue }
            if offset == 0, last.end <= minuteOfDay { continue }
            // Solo dalla prima all'ultima lezione: le ore libere ai bordi non servono
            let slots = effective.timeline().filter { $0.start >= first.start && $0.end <= last.end }
            return DayPlan(date: date, day: effective, slots: slots)
        }
        return nil
    }

    // MARK: - Sincronizzazione

    func refresh(manual: Bool = false) async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }
        do {
            let fresh = try await scheduleSource.fetch()
            if fresh != schedule { schedule = fresh }
            lastSync = Date()
            errorMessage = nil
        } catch {
            if manual || schedule == nil { errorMessage = "Impossibile aggiornare l'orario. Controlla la connessione." }
        }
        await syncSubstitutions()
        save()
    }

    private func syncSubstitutions() async {
        guard api.isEnabled else { return }
        let sent = pending
        do {
            let remote = sent.isEmpty ? try await api.fetch() : try await api.push(sent)
            // Quello che è cambiato mentre la richiesta era in corso resta in coda
            pending.removeAll { sent.contains($0) }
            let merged = recent(mergeWithPending(remote: remote, pending: pending))
            if merged != overrides { overrides = merged }
            save()
        } catch {
            // Le modifiche restano in coda e ripartono al prossimo aggiornamento
        }
    }

    // MARK: - Supplenze

    /// Supplenza (`lesson` presente) o ora libera (`lesson` nil), solo per `date`.
    func setOverride(date: LocalDate, hour: Int, lesson: Lesson?) {
        let override = Override(date: date, hour: hour, lesson: lesson)
        overrides[override.key] = override
        enqueue(PendingChange(date: date, hour: hour, lesson: lesson))
    }

    func clearOverride(date: LocalDate, hour: Int) {
        overrides[OverrideKey(date: date, hour: hour)] = nil
        enqueue(PendingChange(date: date, hour: hour, delete: true))
    }

    private func enqueue(_ change: PendingChange) {
        pending.removeAll { $0.key == change.key }
        pending.append(change)
        save()
        Task { await syncSubstitutions() }
    }

    /// Sul Mac bastano le ultime due settimane: lo storico completo resta nel foglio.
    private func recent(_ overrides: [OverrideKey: Override]) -> [OverrideKey: Override] {
        let oldest = today.adding(days: -14)
        return overrides.filter { $0.key.date >= oldest }
    }

    // MARK: - Disco

    private func load() {
        guard let data = try? Data(contentsOf: storeURL),
              let stored = try? JSONDecoder().decode(Stored.self, from: data) else { return }
        schedule = stored.schedule
        overrides = recent(Dictionary(stored.overrides.map { ($0.key, $0) }, uniquingKeysWith: { _, last in last }))
        pending = stored.pending
        lastSync = stored.lastSync
    }

    private func save() {
        let stored = Stored(schedule: schedule, overrides: Array(overrides.values), pending: pending, lastSync: lastSync)
        if let data = try? JSONEncoder().encode(stored) {
            try? data.write(to: storeURL, options: .atomic)
        }
    }
}
