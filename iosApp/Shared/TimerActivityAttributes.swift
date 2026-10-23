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
        /// The current round of `rounds`; both 1 for a routine without rounds.
        var round: Int = 1
        var rounds: Int = 1

        var stepStart: Date { stepEnd.addingTimeInterval(-stepDuration) }
        /// "Round 2 of 4", or nil without rounds.
        var roundLabel: String? { rounds > 1 ? "Round \(round) of \(rounds)" : nil }
        var isPaused: Bool { pausedRemaining != nil }
    }

    var routineTitle: String
    /// The routine's accent colour as ARGB; nil (or 0) for the app's own colour.
    var accentArgb: Int64? = nil
}
