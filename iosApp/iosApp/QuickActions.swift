import ComposeApp
import UIKit
import UserNotifications
import WidgetKit

/// Routes Home Screen quick actions (long-press the app icon) to the shared code, which starts
/// the routine. SwiftUI has no quick-action API, so a scene delegate receives them.
/// sereneflow:// links and opened .sereneflow files, from whichever of SwiftUI's onOpenURL and the
/// scene delegate delivers them (possibly both, for the same URL).
enum IncomingURL {
    private static var last: (url: URL, at: Date)?

    static func handle(_ url: URL) {
        if let last, last.url == url, Date().timeIntervalSince(last.at) < 2 { return }
        last = (url, Date())
        if url.isFileURL {
            MainViewControllerKt.openRoutineFile(url: url)
        } else {
            MainViewControllerKt.openAppUrl(url: url.absoluteString)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Held here: the notification center keeps its delegate weakly.
    private let reminders = ReminderNotifications()

    /// Set before launch finishes, so a tap on a reminder that launches the app reaches it.
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = reminders
        return true
    }

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
    /// A cold launch from a quick action or a link / file.
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem { handle(item) }
        connectionOptions.urlContexts.forEach { IncomingURL.handle($0.url) }
    }

    /// A link or file while running. With a scene delegate of our own, SwiftUI's onOpenURL may not
    /// be called, so URLs are handled here too (IncomingURL drops the duplicate if both are).
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        URLContexts.forEach { IncomingURL.handle($0.url) }
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
