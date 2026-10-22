import ActivityKit
import Foundation

/// Live Activity model shared by the app (which starts and updates it) and the widget extension
/// (which renders it on the Lock Screen and in the Dynamic Island).
struct TimerActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var stepTitle: String
        var stepIndex: Int
        var stepCount: Int
        var nextStepTitle: String?
        var stepDuration: TimeInterval
        /// When the current step ends. Only meaningful while running.
        var stepEnd: Date
        /// Remaining time of the step while paused; nil while running.
        var pausedRemaining: TimeInterval?
        var isCompleted: Bool

        var stepStart: Date { stepEnd.addingTimeInterval(-stepDuration) }
        var isPaused: Bool { pausedRemaining != nil }
    }

    var routineTitle: String
}
