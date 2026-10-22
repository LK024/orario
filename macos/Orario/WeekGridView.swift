import SwiftUI
import OrarioCore

/// Una colonna della griglia: un'ora oppure la pausa dopo una certa ora.
private enum GridColumn: Hashable {
    case hour(Int)
    case pause(after: Int)
}

/// Ora tenuta sotto il clic destro, per il pannello della supplenza.
struct EditTarget: Identifiable {
    let date: LocalDate
    let dayName: String
    let hour: Int
    let current: Lesson?

    var id: String { "\(date)-\(hour)" }
    var title: String { "\(dayName) \(date.dayMonthText) · \(hour + 1)ª ora" }
}

/// Griglia settimanale come la pagina desktop della PWA: giorni in righe, ore in colonne.
struct WeekGridView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var editing: EditTarget?

    private let dayLabelWidth: CGFloat = 104
    private let pauseWidth: CGFloat = 22
    private let spacing: CGFloat = 6

    var body: some View {
        Group {
            if model.days.isEmpty {
                emptyState
            } else {
                grid
            }
        }
        .navigationTitle("Orario")
        .navigationSubtitle(subtitle)
        .toolbar {
            ToolbarItemGroup {
                Button {
                    Task { await model.refresh(manual: true) }
                } label: {
                    if model.isSyncing {
                        ProgressView().controlSize(.small)
                    } else {
                        Label("Aggiorna", systemImage: "arrow.clockwise")
                    }
                }
                .keyboardShortcut("r")
                .help("Scarica orario e supplenze dal foglio")

                // Pulsante e non Link: così ha lo stesso stile di "Aggiorna" nel gruppo della toolbar
                Button {
                    openURL(Config.sheetEditURL)
                } label: {
                    Label("Modifica il foglio", systemImage: "square.and.pencil")
                }
                .help("Apri il foglio Google")
            }
        }
        .sheet(item: $editing) { target in
            SubstitutionSheet(target: target) { lesson in
                model.setOverride(date: target.date, hour: target.hour, lesson: lesson)
            }
        }
        .alert("Aggiornamento non riuscito", isPresented: Binding(
            get: { model.errorMessage != nil },
            set: { if !$0 { model.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model.errorMessage ?? "")
        }
    }

    private var subtitle: String {
        guard !model.days.isEmpty else { return "" }
        let week = weekRangeText(from: model.date(ofDayAt: 0), to: model.date(ofDayAt: model.days.count - 1))
        guard let lastSync = model.lastSync else { return week }
        return "\(week) · aggiornato alle \(timeText(lastSync))"
    }

    private var columns: [GridColumn] {
        let days = model.days
        let maxHours = max(days.map(\.config.totalHours).max() ?? 0, 1)
        var pauses = Set<Int>()
        for day in days {
            if day.config.breakAfter > 0, day.config.breakDuration > 0 { pauses.insert(day.config.breakAfter) }
            if day.config.lunchAfter > 0, day.config.lunchDuration > 0 { pauses.insert(day.config.lunchAfter) }
        }
        var columns: [GridColumn] = []
        for hour in 0..<maxHours {
            columns.append(.hour(hour))
            if pauses.contains(hour + 1), hour + 1 < maxHours { columns.append(.pause(after: hour + 1)) }
        }
        return columns
    }

    private var grid: some View {
        GeometryReader { geometry in
            let columns = columns
            let headerHeight: CGFloat = 24
            let rows = CGFloat(model.days.count)
            let rowHeight = max(64, (geometry.size.height - 32 - headerHeight - spacing * rows) / rows)

            Grid(horizontalSpacing: spacing, verticalSpacing: spacing) {
                GridRow {
                    Color.clear.frame(width: dayLabelWidth, height: headerHeight)
                    ForEach(columns, id: \.self) { column in
                        header(for: column).frame(height: headerHeight)
                    }
                }
                ForEach(model.days.indices, id: \.self) { index in
                    GridRow {
                        dayLabel(at: index).frame(width: dayLabelWidth, height: rowHeight)
                        ForEach(columns, id: \.self) { column in
                            cell(dayIndex: index, column: column).frame(height: rowHeight)
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    @ViewBuilder
    private func header(for column: GridColumn) -> some View {
        switch column {
        case .hour(let hour):
            Text("\(hour + 1)ª")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
        case .pause(let after):
            let isLunch = model.days.contains { $0.config.lunchAfter == after && $0.config.lunchDuration > 0 }
            Image(systemName: isLunch ? "fork.knife" : "cup.and.saucer")
                .font(.caption2)
                .foregroundStyle(.tertiary)
                .frame(width: pauseWidth)
        }
    }

    private func dayLabel(at index: Int) -> some View {
        let date = model.date(ofDayAt: index)
        let isToday = date == model.today
        return VStack(alignment: .leading, spacing: 2) {
            Text(model.days[index].name)
                .font(.headline)
                .foregroundStyle(isToday ? Color.accentColor : .primary)
            Text(date.dayMonthText)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func cell(dayIndex: Int, column: GridColumn) -> some View {
        let day = model.effectiveDay(at: dayIndex)
        let date = model.date(ofDayAt: dayIndex)
        let isToday = date == model.today
        let slots = day.timeline()

        switch column {
        case .hour(let hour):
            if hour < day.config.totalHours, let slot = slots.first(where: { $0.kind.hour == hour }) {
                let lesson = day.lesson(at: hour)
                let override = model.override(on: date, hour: hour)
                LessonCell(
                    start: slot.start,
                    end: slot.end,
                    lesson: lesson,
                    active: isToday && slot.contains(minute: model.minuteOfDay),
                    override: override
                )
                .contextMenu {
                    Section(EditTarget(date: date, dayName: day.name, hour: hour, current: nil).title) {
                        Button(override?.lesson != nil ? "Modifica supplenza…" : "Segna supplenza…", systemImage: "arrow.left.arrow.right") {
                            editing = EditTarget(date: date, dayName: day.name, hour: hour, current: override?.lesson)
                        }
                        if lesson != nil {
                            Button("Libera solo questo giorno", systemImage: "calendar.badge.minus") {
                                model.setOverride(date: date, hour: hour, lesson: nil)
                            }
                        }
                        if override != nil {
                            Button("Ripristina orario", systemImage: "arrow.uturn.backward") {
                                model.clearOverride(date: date, hour: hour)
                            }
                        }
                    }
                }
            } else {
                Color.clear
            }
        case .pause(let after):
            if let slot = slots.first(where: { $0.kind.pauseAfter(in: day) == after }), case .pause(let kind) = slot.kind {
                PauseCell(kind: kind, active: isToday && slot.contains(minute: model.minuteOfDay))
                    .frame(width: pauseWidth)
            } else {
                Color.clear.frame(width: pauseWidth)
            }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label("Nessun orario salvato", systemImage: "icloud.slash")
        } description: {
            Text("Serve una connessione per scaricarlo la prima volta dal foglio Google.")
        } actions: {
            if model.isSyncing {
                ProgressView()
            } else {
                Button("Scarica l'orario") { Task { await model.refresh(manual: true) } }
            }
        }
    }
}

private extension Slot.Kind {
    var hour: Int? {
        if case .lesson(let hour, _) = self { return hour }
        return nil
    }

    /// Dopo quale ora (contando da 1) cade questa pausa nel giorno.
    func pauseAfter(in day: Day) -> Int? {
        guard case .pause(let kind) = self else { return nil }
        return kind == .recess ? day.config.breakAfter : day.config.lunchAfter
    }
}
