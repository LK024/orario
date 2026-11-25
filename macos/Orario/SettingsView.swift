import OrarioCore
import ServiceManagement
import SwiftUI

enum SettingsKey {
    static let showInMenuBar = "showInMenuBar"
    /// Scritto dall'app: vero dalla prima all'ultima lezione di oggi, fuori dalla pausa.
    static let schoolTime = "menuBarSchoolTime"
}

/// Impostazioni dell'app (⌘,).
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @AppStorage(SettingsKey.showInMenuBar) private var showInMenuBar = true
    @State private var launchAtLogin = SMAppService.mainApp.status == .enabled
    @State private var pauseFrom = Date()
    @State private var pauseTo = Date()

    var body: some View {
        Form {
            Section {
                Toggle("Mostra nella barra dei menu", isOn: $showInMenuBar)
                Text(showInMenuBar
                     ? "Dalla prima all'ultima lezione di oggi la lezione in corso resta in alto a destra. Chiudendo la finestra l'app rimane attiva."
                     : "Chiudendo la finestra l'app si chiude del tutto.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Section {
                Toggle("Apri all'accesso", isOn: $launchAtLogin)
                    .onChange(of: launchAtLogin) { _, enabled in
                        try? enabled ? SMAppService.mainApp.register() : SMAppService.mainApp.unregister()
                        launchAtLogin = SMAppService.mainApp.status == .enabled
                    }
                Text("Così dopo un riavvio l'icona torna da sola all'inizio della giornata.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            pauseSection
        }
        .formStyle(.grouped)
        .frame(width: 420)
        .fixedSize()
        // Una pausa messa dal telefono si vede appena si aprono le impostazioni
        .onAppear { model.syncPauseNow() }
    }

    /// La stessa pausa dei promemoria sul telefono: passa dal foglio, quindi vale ovunque.
    private var pauseSection: some View {
        Section("Pausa") {
            if let pause = model.currentPause {
                LabeledContent("In pausa", value: "dal \(pause.from.dayMonthText) al \(pause.to.dayMonthText)")
                Button("Riprendi") { model.setPause(nil) }
            } else {
                DatePicker("Dal", selection: $pauseFrom, displayedComponents: .date)
                    .onChange(of: pauseFrom) { _, from in pauseTo = max(pauseTo, from) }
                DatePicker("Al", selection: $pauseTo, in: pauseFrom..., displayedComponents: .date)
                Button("Metti in pausa") {
                    model.setPause(PausePeriod(from: LocalDate(pauseFrom), to: LocalDate(pauseTo)))
                }
            }
            Text("In pausa l'icona non compare e il telefono non manda promemoria né il Live Update.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
