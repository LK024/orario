import AppKit
import SwiftUI

@main
struct OrarioApp: App {
    @NSApplicationDelegateAdaptor private var appDelegate: AppDelegate
    @State private var model = AppModel()
    @AppStorage(SettingsKey.showInMenuBar) private var showInMenuBar = true
    // Lo scrive il modello a ogni cambio d'ora o di pausa: una preferenza fa ridisegnare la scena di sicuro
    @AppStorage(SettingsKey.schoolTime) private var schoolTime = false

    var body: some Scene {
        Window("Orario", id: "main") {
            WeekGridView()
                .environment(model)
                .frame(minWidth: 860, minHeight: 440)
        }
        .defaultSize(width: 1180, height: 600)

        Settings {
            SettingsView()
                .environment(model)
        }

        // Come il Live Update su Android: l'icona c'è solo dalla prima all'ultima lezione di oggi.
        // Con l'opzione attiva l'app resta aperta anche a finestra chiusa, pronta per il giorno dopo
        // Il setter resta vuoto: quando l'icona sparisce da sola SwiftUI lo riporta qui, e non deve
        // diventare "opzione disattivata". L'opzione si cambia solo dalle Impostazioni
        MenuBarExtra(isInserted: Binding(get: { showInMenuBar && schoolTime }, set: { _ in })) {
            MenuBarContent()
                .environment(model)
        } label: {
            MenuBarLabel(model: model)
        }
        .menuBarExtraStyle(.window)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    /// Con l'opzione della barra dei menu attiva l'app resta aperta anche senza finestre, pronta a mostrare
    /// l'icona all'inizio della giornata; senza, chiudere la finestra chiude l'app.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        !(UserDefaults.standard.object(forKey: SettingsKey.showInMenuBar) as? Bool ?? true)
    }
}
