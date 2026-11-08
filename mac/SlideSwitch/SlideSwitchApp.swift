import SwiftUI

@main
struct SlideSwitchApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        Window("SlideSwitch", id: "main") {
            RootView()
                .environment(model)
                .frame(minWidth: 1000, minHeight: 700)
        }
        .windowStyle(.hiddenTitleBar)
        .windowBackgroundDragBehavior(.enabled)
        .windowResizability(.contentMinSize)
        .defaultSize(width: 1120, height: 760)
        .commands {
            CommandGroup(after: .appInfo) {
                Button("New Pairing Code") {
                    withAnimation(.snappy) { model.regenerateCode() }
                }
                .keyboardShortcut("r")
            }
        }
    }
}
