import SwiftUI
import OrarioCore

/// Pannello per segnare una supplenza su un'ora, valida solo per quel giorno.
struct SubstitutionSheet: View {
    let target: EditTarget
    let onSave: (Lesson) -> Void

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var room = ""
    @State private var teacher = ""
    @FocusState private var focusedOnTitle: Bool

    var body: some View {
        let suggestions = model.suggestions
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Supplenza").font(.title2.weight(.semibold))
                Text(target.title).foregroundStyle(.secondary)
                Text("Vale solo per questo giorno: dalla settimana dopo torna l'orario normale.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Form {
                TextField("Classe", text: $title)
                    .focused($focusedOnTitle)
                    .textInputSuggestions(matches(suggestions.titles, title), id: \.self) { Text($0).textInputCompletion($0) }
                TextField("Aula", text: $room)
                    .textInputSuggestions(matches(suggestions.rooms, room), id: \.self) { Text($0).textInputCompletion($0) }
                TextField("Docente (facoltativo)", text: $teacher)
                    .textInputSuggestions(matches(suggestions.teachers, teacher), id: \.self) { Text($0).textInputCompletion($0) }
            }
            .formStyle(.grouped)

            HStack {
                Spacer()
                Button("Annulla", role: .cancel) { dismiss() }
                    .keyboardShortcut(.cancelAction)
                Button("Salva") { save() }
                    .keyboardShortcut(.defaultAction)
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 420)
        .onAppear {
            title = target.current?.title ?? ""
            room = target.current?.room ?? ""
            teacher = target.current?.teacher ?? ""
            focusedOnTitle = true
        }
    }

    private func matches(_ values: [String], _ text: String) -> [String] {
        let typed = text.trimmingCharacters(in: .whitespaces)
        return values.filter { typed.isEmpty || ($0.localizedCaseInsensitiveContains(typed) && $0 != typed) }
    }

    private func save() {
        func clean(_ text: String) -> String? {
            let trimmed = text.trimmingCharacters(in: .whitespaces)
            return trimmed.isEmpty ? nil : trimmed
        }
        guard let lessonTitle = clean(title) else { return }
        onSave(Lesson(title: lessonTitle, room: clean(room), teacher: clean(teacher)))
        dismiss()
    }
}
