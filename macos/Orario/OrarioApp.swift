import SwiftUI

@main
struct OrarioApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        Window("Orario", id: "main") {
            WeekGridView()
                .environment(model)
                .frame(minWidth: 860, minHeight: 440)
        }
        .defaultSize(width: 1180, height: 600)

        // Resta attiva anche a finestra chiusa: la lezione in corso è sempre in alto a destra
        MenuBarExtra {
            MenuBarContent()
                .environment(model)
        } label: {
            MenuBarLabel(model: model)
        }
        .menuBarExtraStyle(.window)
    }
}
