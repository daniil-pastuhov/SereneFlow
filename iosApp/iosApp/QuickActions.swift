import ComposeApp
import UIKit
import WidgetKit

/// Routes Home Screen quick actions (long-press the app icon) to the shared code, which starts
/// the routine. SwiftUI has no quick-action API, so a scene delegate receives them.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }
}

final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    /// A cold launch from a quick action.
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem { handle(item) }
    }

    /// A quick action while the app is running or suspended.
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(handle(shortcutItem))
    }

    /// Apple's advice: update quick actions as the app leaves the foreground. The shared code
    /// keeps the list (last-started routine first); the bindings lack `shortcutItems`, so it's set here.
    func sceneWillResignActive(_ scene: UIScene) {
        UIApplication.shared.shortcutItems = QuickStart_iosKt.currentQuickStartItems().map { item in
            UIApplicationShortcutItem(
                type: QuickStart_iosKt.START_ROUTINE_SHORTCUT,
                localizedTitle: item.title,
                localizedSubtitle: item.subtitle,
                icon: UIApplicationShortcutIcon(systemImageName: "play.fill"),
                userInfo: [QuickStart_iosKt.SHORTCUT_ROUTINE_ID: item.routineId as NSString]
            )
        }
    }

    /// The widget shows the routines the app last published; refresh it as the user leaves.
    func sceneDidEnterBackground(_ scene: UIScene) {
        WidgetCenter.shared.reloadAllTimelines()
    }

    @discardableResult
    private func handle(_ item: UIApplicationShortcutItem) -> Bool {
        guard item.type == QuickStart_iosKt.START_ROUTINE_SHORTCUT,
              let routineId = item.userInfo?[QuickStart_iosKt.SHORTCUT_ROUTINE_ID] as? String
        else { return false }
        MainViewControllerKt.startRoutineFromShortcut(routineId: routineId)
        return true
    }
}
