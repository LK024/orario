import ServiceManagement
import SwiftUI
import OrarioCore

extension AppModel {
    /// Le ore di lezione di oggi, con supplenze applicate (vuoto nei giorni senza scuola).
    private var todayLessons: [(hour: Int, lesson: Lesson?, start: Int, end: Int)] {
        guard let day = days.first(where: { $0.weekday == today.weekday }) else { return [] }
        return day.forDate(today, overrides: overrides).lessonSlots().filter { $0.lesson != nil }
    }

    var currentLesson: (lesson: Lesson, end: Int)? {
        todayLessons.first { minuteOfDay >= $0.start && minuteOfDay < $0.end }.map { ($0.lesson!, $0.end) }
    }

    var nextLessonToday: (lesson: Lesson, start: Int)? {
        todayLessons.first { $0.start > minuteOfDay }.map { ($0.lesson!, $0.start) }
    }
}

/// Testo nella barra dei menu: la lezione in corso, oppure la prossima di oggi.
struct MenuBarLabel: View {
    let model: AppModel

    var body: some View {
        if let current = model.currentLesson {
            Label("\(current.lesson.title) · fino alle \(formatMinutes(current.end))", systemImage: "studentdesk")
                .labelStyle(.titleAndIcon)
        } else if let next = model.nextLessonToday {
            Label("\(next.lesson.title) alle \(formatMinutes(next.start))", systemImage: "studentdesk")
                .labelStyle(.titleAndIcon)
        } else {
            Image(systemName: "studentdesk")
        }
    }
}

/// Riquadro che si apre dalla barra dei menu: la giornata (oggi o il prossimo giorno di scuola).
struct MenuBarContent: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    @State private var launchAtLogin = SMAppService.mainApp.status == .enabled

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let plan = model.upcomingDay {
                Text(heading(for: plan.date))
                    .font(.headline)
                VStack(spacing: 4) {
                    ForEach(plan.slots, id: \.self) { slot in
                        row(slot, isToday: plan.date == model.today, date: plan.date)
                    }
                }
            } else {
                Text("Nessuna lezione nei prossimi giorni")
                    .foregroundStyle(.secondary)
            }

            Divider()

            Toggle("Apri all'accesso", isOn: $launchAtLogin)
                .toggleStyle(.checkbox)
                .onChange(of: launchAtLogin) { _, enabled in
                    try? enabled ? SMAppService.mainApp.register() : SMAppService.mainApp.unregister()
                    launchAtLogin = SMAppService.mainApp.status == .enabled
                }

            HStack {
                Button("Apri Orario") {
                    openWindow(id: "main")
                    NSApp.activate()
                }
                Spacer()
                Button {
                    Task { await model.refresh(manual: true) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .help("Aggiorna")
                .disabled(model.isSyncing)
                Button("Esci") { NSApp.terminate(nil) }
            }
        }
        .padding(14)
        .frame(width: 300)
    }

    private func heading(for date: LocalDate) -> String {
        if date == model.today { return "Oggi · \(date.fullText)" }
        if date == model.today.adding(days: 1) { return "Domani · \(date.fullText)" }
        return date.fullText.prefix(1).uppercased() + date.fullText.dropFirst()
    }

    @ViewBuilder
    private func row(_ slot: Slot, isToday: Bool, date: LocalDate) -> some View {
        let active = isToday && slot.contains(minute: model.minuteOfDay)
        switch slot.kind {
        case .lesson(let hour, let lesson):
            HStack(spacing: 10) {
                Text(formatMinutes(slot.start))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(width: 36, alignment: .leading)
                if let lesson {
                    Text(lesson.title).fontWeight(.semibold)
                    if let room = lesson.room {
                        Text(room).font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    Text("Libera").foregroundStyle(.tertiary)
                }
                Spacer()
                if model.override(on: date, hour: hour) != nil {
                    Image(systemName: "arrow.left.arrow.right").font(.caption).foregroundStyle(.orange)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(active ? Color.accentColor.opacity(0.18) : .clear, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        case .pause(let kind):
            HStack {
                VStack { Divider() }
                Text("\(kind.label) · \(formatMinutes(slot.start))")
                    .font(.caption2)
                    .foregroundStyle(active ? Color.accentColor : .secondary)
                VStack { Divider() }
            }
        }
    }
}
