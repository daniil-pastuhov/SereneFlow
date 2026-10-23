import SwiftUI
import WatchConnectivity

/// Apple Watch companion: shows the routine running on the iPhone, sent over WatchConnectivity by
/// the iPhone app's WatchSessionManager. The timer itself runs on the phone; the watch mirrors it
/// and taps on step changes.
@main
struct SereneFlowWatchApp: App {
    @StateObject private var model = WatchSessionModel()

    var body: some Scene {
        WindowGroup {
            WatchTimerView(model: model)
        }
    }
}

/// The routine state as the phone last sent it (see WatchSessionManager.swift for the keys).
struct WatchSnapshot: Equatable {
    var routineTitle: String
    var stepTitle: String
    var stepIndex: Int
    var stepCount: Int
    var nextStepTitle: String?
    var stepDuration: TimeInterval
    /// When the step ends; nil while paused.
    var stepEnd: Date?
    var remaining: TimeInterval
    var isPaused: Bool
    var isCompleted: Bool
    /// The current round of `rounds`; both 1 without rounds.
    var round: Int
    var rounds: Int

    init?(_ context: [String: Any]) {
        guard context["active"] as? Bool == true,
              let routineTitle = context["routineTitle"] as? String,
              let stepTitle = context["stepTitle"] as? String
        else { return nil }
        self.routineTitle = routineTitle
        self.stepTitle = stepTitle
        stepIndex = context["stepIndex"] as? Int ?? 0
        stepCount = context["stepCount"] as? Int ?? 1
        nextStepTitle = context["nextStepTitle"] as? String
        stepDuration = context["stepDuration"] as? Double ?? 0
        stepEnd = (context["stepEnd"] as? Double).map { Date(timeIntervalSince1970: $0) }
        remaining = context["remaining"] as? Double ?? 0
        isPaused = context["isPaused"] as? Bool ?? false
        isCompleted = context["isCompleted"] as? Bool ?? false
        round = context["round"] as? Int ?? 1
        rounds = context["rounds"] as? Int ?? 1
    }
}

@MainActor
final class WatchSessionModel: NSObject, ObservableObject, WCSessionDelegate {
    @Published private(set) var snapshot: WatchSnapshot?

    override init() {
        super.init()
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    private func apply(_ context: [String: Any]) {
        let new = WatchSnapshot(context)
        if let old = snapshot, let new, new.stepIndex != old.stepIndex || (new.isCompleted && !old.isCompleted) {
            WKInterfaceDeviceHaptics.play(completed: new.isCompleted)
        }
        snapshot = new
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        let context = session.receivedApplicationContext
        Task { @MainActor in self.apply(context) }
    }

    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        Task { @MainActor in self.apply(applicationContext) }
    }
}
