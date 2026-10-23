import ActivityKit
import ComposeApp
import Foundation

/// Swift side of the Kotlin `LiveActivityBridge`: ActivityKit is Swift-only, so the shared code
/// calls into this object (on the main thread) to start, update and end the routine's Live Activity.
final class LiveActivityManager: NSObject, LiveActivityBridge {
    /// `Activity<TimerActivityAttributes>`; typed `Any` so the stored property needs no availability check.
    private var activity: Any?
    private var lastState: Any?

    override init() {
        super.init()
        // An activity left over from a previous run (app killed mid-routine) no longer has a session.
        if #available(iOS 16.2, *) {
            for stale in Activity<TimerActivityAttributes>.activities {
                Task { await stale.end(nil, dismissalPolicy: .immediate) }
            }
        }
    }

    func startOrUpdate(
        routineTitle: String,
        stepTitle: String,
        stepIndex: Int32,
        stepCount: Int32,
        nextStepTitle: String?,
        stepDurationSeconds: Double,
        stepEndEpochSeconds: Double,
        remainingSeconds: Double,
        isPaused: Bool,
        isCompleted: Bool,
        accentArgb: Int64
    ) {
        guard #available(iOS 16.2, *) else { return }
        let state = TimerActivityAttributes.ContentState(
            stepTitle: stepTitle,
            stepIndex: Int(stepIndex),
            stepCount: Int(stepCount),
            nextStepTitle: nextStepTitle,
            stepDuration: stepDurationSeconds,
            stepEnd: isPaused || isCompleted
                ? Date().addingTimeInterval(remainingSeconds)
                : Date(timeIntervalSince1970: stepEndEpochSeconds),
            pausedRemaining: isPaused && !isCompleted ? remainingSeconds : nil,
            isCompleted: isCompleted
        )
        // The shared code also sends periodic refreshes for Android's progress bar; the Live
        // Activity animates its own countdown, so only real changes are forwarded.
        if let last = lastState as? TimerActivityAttributes.ContentState, last.sameDisplay(as: state) { return }
        lastState = state

        // Lets the system mark the activity stale if the app can't update it at the step boundary.
        let content = ActivityContent(
            state: state,
            staleDate: state.isPaused || isCompleted ? nil : state.stepEnd.addingTimeInterval(2)
        )

        if let current = activity as? Activity<TimerActivityAttributes> {
            Task {
                if isCompleted {
                    // Keep the "complete" card briefly on the Lock Screen, then let it go.
                    await current.end(content, dismissalPolicy: .after(Date().addingTimeInterval(15 * 60)))
                } else {
                    await current.update(content)
                }
            }
            if isCompleted { activity = nil }
        } else if !isCompleted {
            guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
            do {
                activity = try Activity.request(
                    attributes: TimerActivityAttributes(
                        routineTitle: routineTitle,
                        accentArgb: accentArgb == 0 ? nil : accentArgb
                    ),
                    content: content,
                    pushType: nil
                )
            } catch {
                NSLog("SereneFlow: Live Activity request failed: \(error)")
            }
        }
    }

    func end() {
        guard #available(iOS 16.2, *) else { return }
        lastState = nil
        // Ends the running one and any lingering "complete" card.
        let current = activity as? Activity<TimerActivityAttributes>
        activity = nil
        // Listed now, not inside the Task: a routine restarted right after this may request its new
        // activity before the Task runs, and that one must survive.
        let others = Activity<TimerActivityAttributes>.activities.filter { $0.id != current?.id }
        Task {
            await current?.end(nil, dismissalPolicy: .immediate)
            for other in others {
                await other.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}

private extension TimerActivityAttributes.ContentState {
    /// Equal for display purposes: running end times within a second count as the same.
    func sameDisplay(as other: Self) -> Bool {
        stepTitle == other.stepTitle && stepIndex == other.stepIndex && stepCount == other.stepCount
            && nextStepTitle == other.nextStepTitle && isCompleted == other.isCompleted
            && isPaused == other.isPaused
            && (isPaused || abs(stepEnd.timeIntervalSince(other.stepEnd)) < 1)
            && (!isPaused || pausedRemaining == other.pausedRemaining)
    }
}
