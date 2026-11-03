import ComposeApp
import UserNotifications

/// Routine reminders (scheduled by the shared code, RoutineReminders.ios.kt): shown even while the
/// app is open, and a tap starts the routine. The session's own banners are only posted while the
/// app is in the background, and stay hidden in the foreground as before.
final class ReminderNotifications: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler(Self.routineId(of: notification) != nil ? [.banner, .list, .sound] : [])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier,
           let routineId = Self.routineId(of: response.notification) {
            MainViewControllerKt.startRoutineFromShortcut(routineId: routineId)
        }
        completionHandler()
    }

    private static func routineId(of notification: UNNotification) -> String? {
        let request = notification.request
        guard request.identifier.hasPrefix(RoutineReminders_iosKt.REMINDER_ID_PREFIX) else { return nil }
        return request.content.userInfo[RoutineReminders_iosKt.REMINDER_ROUTINE_ID] as? String
    }
}
