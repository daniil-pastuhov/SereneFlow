import ComposeApp
import SwiftUI

@main
struct SereneFlowApp: App {
    init() {
        // Lets the shared timer session drive the Live Activity / Dynamic Island.
        LiveActivityRegistry.shared.bridge = LiveActivityManager()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
