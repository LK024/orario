import ServiceManagement
import SwiftUI

enum SettingsKey {
    static let showInMenuBar = "showInMenuBar"
}

/// Impostazioni dell'app (⌘,).
struct SettingsView: View {
    @AppStorage(SettingsKey.showInMenuBar) private var showInMenuBar = true
    @State private var launchAtLogin = SMAppService.mainApp.status == .enabled

    var body: some View {
        Form {
            Section {
                Toggle("Mostra nella barra dei menu", isOn: $showInMenuBar)
                Text(showInMenuBar
                     ? "La lezione in corso resta in alto a destra e chiudendo la finestra l'app rimane attiva."
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
            }
        }
        .formStyle(.grouped)
        .frame(width: 420)
        .fixedSize()
    }
}
