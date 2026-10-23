import ComposeApp
import SwiftUI

@main
struct SereneFlowApp: App {
    // Home Screen quick actions (QuickActions.swift).
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // Lets the shared timer session drive the Live Activity / Dynamic Island.
        LiveActivityRegistry.shared.bridge = LiveActivityManager()
        // Lets Settings → App icon switch the alternate app icons.
        AppIconRegistry.shared.bridge = AppIconManager()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
