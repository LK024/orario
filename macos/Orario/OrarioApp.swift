import AppKit
import SwiftUI

@main
struct OrarioApp: App {
    @NSApplicationDelegateAdaptor private var appDelegate: AppDelegate
    @State private var model = AppModel()
    @AppStorage(SettingsKey.showInMenuBar) private var showInMenuBar = true

    var body: some Scene {
        Window("Orario", id: "main") {
            WeekGridView()
                .environment(model)
                .frame(minWidth: 860, minHeight: 440)
        }
        .defaultSize(width: 1180, height: 600)

        Settings {
            SettingsView()
        }

        // Con l'icona visibile l'app resta attiva anche a finestra chiusa
        MenuBarExtra(isInserted: $showInMenuBar) {
            MenuBarContent()
                .environment(model)
        } label: {
            MenuBarLabel(model: model)
        }
        .menuBarExtraStyle(.window)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    /// Senza l'icona nella barra dei menu, chiudere la finestra chiude l'app.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        !(UserDefaults.standard.object(forKey: SettingsKey.showInMenuBar) as? Bool ?? true)
    }
}
