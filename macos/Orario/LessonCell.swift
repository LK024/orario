import SwiftUI
import OrarioCore

/// Casella di un'ora nella griglia, con orari agli angoli come nella PWA desktop.
struct LessonCell: View {
    let start: Int
    let end: Int
    let lesson: Lesson?
    let active: Bool
    let override: Override?

    private let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)

    var body: some View {
        ZStack {
            shape.fill(fill)
            shape.strokeBorder(borderColor, style: StrokeStyle(lineWidth: active ? 2 : 1, dash: lesson == nil && !active ? [4, 3] : []))

            VStack(spacing: 2) {
                if let lesson {
                    Text(lesson.title)
                        .font(.title3.weight(.semibold))
                    if let room = lesson.room {
                        Text(room).font(.caption).foregroundStyle(.secondary)
                    }
                    if let teacher = lesson.teacher {
                        Text(teacher).font(.caption2).foregroundStyle(.tertiary)
                    }
                } else {
                    Text(override == nil ? "–" : "Libera")
                        .font(override == nil ? .body : .caption)
                        .foregroundStyle(.tertiary)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, 6)

            corners
        }
        .contentShape(shape)
        .animation(.easeInOut(duration: 0.3), value: active)
        .help(helpText)
    }

    private var fill: AnyShapeStyle {
        if active { return AnyShapeStyle(Color.accentColor.opacity(0.18)) }
        if lesson == nil { return AnyShapeStyle(Color.clear) }
        return AnyShapeStyle(.quaternary.opacity(0.7))
    }

    private var borderColor: Color {
        if active { return .accentColor }
        if override != nil { return .orange.opacity(0.8) }
        return lesson == nil ? .secondary.opacity(0.3) : .clear
    }

    private var corners: some View {
        VStack {
            HStack(alignment: .top) {
                Text(formatMinutes(start))
                Spacer()
                if let override {
                    Image(systemName: override.lesson == nil ? "calendar.badge.minus" : "arrow.left.arrow.right")
                        .foregroundStyle(.orange)
                }
            }
            Spacer()
            HStack {
                Spacer()
                Text(formatMinutes(end))
            }
        }
        .font(.system(size: 9, weight: .medium).monospacedDigit())
        .foregroundStyle(.secondary)
        .padding(6)
    }

    private var helpText: String {
        guard let override else { return "" }
        return override.lesson == nil ? "Ora libera solo per questo giorno" : "Supplenza"
    }
}

/// Colonna stretta dell'intervallo o del pranzo, evidenziata quando è in corso.
struct PauseCell: View {
    let kind: BreakKind
    let active: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: 8, style: .continuous)
            .fill(active ? AnyShapeStyle(Color.accentColor.opacity(0.25)) : AnyShapeStyle(.quaternary.opacity(0.35)))
            .overlay {
                Text(kind.label.uppercased())
                    .font(.system(size: 9, weight: .semibold))
                    .tracking(1.5)
                    .fixedSize()
                    .rotationEffect(.degrees(-90))
                    .foregroundStyle(active ? Color.accentColor : .secondary)
            }
    }
}
