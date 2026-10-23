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
        // Siri / Shortcuts / Live Activity buttons (TimerControlIntents.swift) reach the session.
        TimerControl.handler = { control in
            let session = TimerSession.shared
            guard session.active.value != nil else { return false }
            switch control {
            case .toggle: session.togglePlayPause()
            case .skip: session.skipStep()
            case .end: session.stop()
            }
            return true
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
