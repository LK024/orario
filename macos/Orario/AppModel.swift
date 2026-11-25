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
    /// Pausa condivisa con il telefono tramite il foglio: in pausa l'icona nella barra dei menu non compare.
    private(set) var pause: PausePeriod?
    var errorMessage: String?

    /// Settimana mostrata rispetto a quella corrente: 0 questa, 1 la prossima, -1 la scorsa.
    var weekOffset = 0
    @ObservationIgnored private var offsetDay = LocalDate(Date())

    private var pending: [PendingChange] = []
    /// La pausa cambiata qui e non ancora arrivata nel foglio.
    private var pausePending = false
    private let scheduleSource = ScheduleSource(csvURL: Config.sheetCSVURL)
    private let api = SubstitutionsAPI(url: Config.syncURL, token: Config.syncToken)
    @ObservationIgnored private var nextTick: DispatchSourceTimer?
    @ObservationIgnored private var backgroundRefresh: NSBackgroundActivityScheduler?

    private struct Stored: Codable {
        var schedule: Schedule?
        var overrides: [Override]
        var pending: [PendingChange]
        var lastSync: Date?
        // Opzionali: i file salvati dalle versioni precedenti non li hanno
        var pause: PausePeriod?
        var pausePending: Bool?
    }

    private let storeURL: URL = {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Orario", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("state.json")
    }()

    init() {
        load()
        scheduleNextTick()
        startBackgroundRefresh()
        // Dopo lo stop del Mac l'ora e l'evidenziazione vanno ricalcolate subito
        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didWakeNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.tick()
                self?.refreshIfStale()
            }
        }
        // Schermo riacceso o app in primo piano: è il momento in cui si guarda l'ora in corso
        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.screensDidWakeNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.refreshClock() }
        }
        NotificationCenter.default.addObserver(
            forName: NSApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.refreshClock() }
        }
        // Cambio di data, di ora o di fuso: come gli avvisi che riceve l'app Android
        for name in [Notification.Name.NSCalendarDayChanged, .NSSystemClockDidChange, .NSSystemTimeZoneDidChange] {
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.tick() }
            }
        }
        Task { await refresh() }
    }

    // MARK: - Tempo

    var today: LocalDate { LocalDate(now) }
    var minuteOfDay: Int { Calendar.current.component(.hour, from: now) * 60 + Calendar.current.component(.minute, from: now) }
    var weekStart: LocalDate { today.displayedWeekStart.adding(days: 7 * weekOffset) }

    func goToCurrentWeek() { weekOffset = 0 }

    /// L'icona nella barra dei menu compare solo dalla prima all'ultima lezione di oggi, come il Live Update.
    var isSchoolTime: Bool {
        schedule?.liveSpan(on: today, overrides: overrides, pause: pause)?.contains(minuteOfDay) ?? false
    }

    /// Come le sveglie del Live Update su Android: niente controllo ogni minuto, un solo risveglio al
    /// prossimo cambio d'ora (o a mezzanotte). Il timer "strict" non viene rimandato dal risparmio energetico.
    private func armTimer() {
        nextTick?.cancel()
        let calendar = Calendar.current
        let change = schedule?.nextChange(on: today, after: minuteOfDay, overrides: overrides)
        let fire = change.flatMap { calendar.date(bySettingHour: $0 / 60, minute: $0 % 60, second: 0, of: now) }
            ?? calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now))!
        let timer = DispatchSource.makeTimerSource(flags: .strict, queue: .main)
        // Mai meno di mezzo secondo: se scatta un attimo prima del cambio non riparte in un ciclo stretto
        timer.schedule(wallDeadline: .now() + max(fire.timeIntervalSinceNow, 0.5), leeway: .milliseconds(100))
        timer.setEventHandler { [weak self] in
            MainActor.assumeIsolated { self?.tick() }
        }
        timer.resume()
        nextTick = timer
    }

    /// Passa all'app se l'icona va mostrata. Tramite le preferenze e non con un valore calcolato: la
    /// scena dell'app non si ridisegna in modo affidabile quando cambia solo il modello.
    private func updateMenuBar() {
        let visible = isSchoolTime
        if UserDefaults.standard.object(forKey: SettingsKey.schoolTime) as? Bool != visible {
            UserDefaults.standard.set(visible, forKey: SettingsKey.schoolTime)
        }
    }

    /// Dopo una modifica ai dati (orario, supplenze, pausa): ricalcola l'icona e il prossimo risveglio.
    private func scheduleNextTick() {
        updateClock()
        updateMenuBar()
    }

    /// Rilegge l'ora e riarma il timer, senza rete: subito dopo un risveglio può mancare.
    private func updateClock() {
        now = Date()
        // La finestra resta aperta per giorni: al cambio di giorno si torna alla settimana corrente
        if today != offsetDay {
            weekOffset = 0
            offsetDay = today
        }
        armTimer()
    }

    /// All'apertura e al ritorno in primo piano: l'ora in corso è giusta anche se un risveglio è saltato.
    func refreshClock() {
        updateClock()
        // Il timer appena riarmato poteva essere quello che cambia l'icona: in quel caso lo fa qui
        if isSchoolTime != UserDefaults.standard.bool(forKey: SettingsKey.schoolTime) { checkPauseAndUpdateMenuBar() }
    }

    private func tick() {
        updateClock()
        checkPauseAndUpdateMenuBar()
    }

    /// Come sul telefono: prima di mostrare o togliere l'icona controlla se la pausa è cambiata altrove.
    private func checkPauseAndUpdateMenuBar() {
        Task {
            await syncPause()
            updateMenuBar()
        }
    }

    /// Come WorkManager su Android: un aggiornamento completo ogni 6 ore, quando al sistema conviene. Per il
    /// resto si aggiorna all'apertura e controlla la pausa a ogni cambio d'ora.
    private func startBackgroundRefresh() {
        let activity = NSBackgroundActivityScheduler(identifier: "io.github.lk024.orario.refresh")
        activity.repeats = true
        activity.interval = 6 * 60 * 60
        activity.tolerance = 30 * 60
        activity.qualityOfService = .utility
        activity.schedule { [weak self] completion in
            Task { @MainActor in
                await self?.refresh()
                completion(.finished)
            }
        }
        backgroundRefresh = activity
    }

    /// All'apertura della finestra o del menu, e al risveglio: aggiorna se i dati hanno più di 15 minuti.
    func refreshIfStale() {
        guard !isSyncing, Date().timeIntervalSince(lastSync ?? .distantPast) > 15 * 60 else { return }
        Task { await refresh() }
    }

    // MARK: - Dati per l'interfaccia

    var days: [Day] { schedule?.days ?? [] }

    func date(ofDayAt index: Int) -> LocalDate { days[index].date(inWeekStarting: weekStart, index: index) }

    /// Il giorno con supplenze e ore libere già applicate.
    func effectiveDay(at index: Int) -> Day { days[index].forDate(date(ofDayAt: index), overrides: overrides) }

    func override(on date: LocalDate, hour: Int) -> Override? { overrides[OverrideKey(date: date, hour: hour)] }

    /// La lezione dell'orario fisso, prima di supplenze e ore libere.
    func originalLesson(dayAt index: Int, hour: Int) -> Lesson? { days[index].lesson(at: hour) }

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
        await syncPause()
        save()
        scheduleNextTick()
    }

    private func syncSubstitutions() async {
        guard api.isEnabled else { return }
        let sent = pending
        do {
            let remote = sent.isEmpty ? try await api.fetch() : try await api.push(sent)
            // Quello che è cambiato mentre la richiesta era in corso resta in coda
            pending.removeAll { sent.contains($0) }
            let merged = mergeWithPending(remote: remote, pending: pending)
            if merged != overrides { overrides = merged }
            save()
            scheduleNextTick()
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
        scheduleNextTick()
        Task { await syncSubstitutions() }
    }

    // MARK: - Pausa

    /// La pausa in corso o in arrivo (una già finita non conta più).
    var currentPause: PausePeriod? { pause.flatMap { $0.to >= today ? $0 : nil } }

    /// Imposta (o toglie, con nil) la pausa qui e la manda al foglio, da cui la prende il telefono.
    func setPause(_ pause: PausePeriod?) {
        self.pause = pause
        pausePending = true
        save()
        scheduleNextTick()
        Task { await syncPause() }
    }

    /// Legge subito la pausa dal foglio (o invia quella in attesa), senza aspettare l'aggiornamento periodico.
    func syncPauseNow() {
        Task { await syncPause() }
    }

    /// Come per le supplenze: la pausa cambiata qui e non ancora inviata vince su quella del foglio.
    private func syncPause() async {
        guard api.isEnabled else { return }
        let sent = pause
        do {
            let remote = pausePending ? try await api.pushPause(sent) : try await api.fetchPause()
            // Script non ancora aggiornato: resta tutto com'è, e la modifica resta da inviare
            guard case .period(let value) = remote else { return }
            // Cambiata di nuovo durante la richiesta: la invia il prossimo aggiornamento
            guard pause == sent else { return }
            pause = value
            pausePending = false
            save()
            scheduleNextTick()
        } catch {
            // Ci riprova al prossimo aggiornamento
        }
    }

    // MARK: - Disco

    private func load() {
        guard let data = try? Data(contentsOf: storeURL),
              let stored = try? JSONDecoder().decode(Stored.self, from: data) else { return }
        schedule = stored.schedule
        overrides = Dictionary(stored.overrides.map { ($0.key, $0) }, uniquingKeysWith: { _, last in last })
        pending = stored.pending
        lastSync = stored.lastSync
        pause = stored.pause
        pausePending = stored.pausePending ?? false
    }

    private func save() {
        let stored = Stored(schedule: schedule, overrides: Array(overrides.values), pending: pending, lastSync: lastSync,
                            pause: pause, pausePending: pausePending)
        if let data = try? JSONEncoder().encode(stored) {
            try? data.write(to: storeURL, options: .atomic)
        }
    }
}
