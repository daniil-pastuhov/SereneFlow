import SwiftUI
import WatchConnectivity
import WatchKit

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

    /// How long past its step's end a running snapshot is still believed. The phone refreshes at
    /// least every 5 s while a step runs and sends a new one on each step change, so a snapshot this
    /// old means the phone app was killed or suspended without saying so.
    static let staleAfter: TimeInterval = 30

    /// A running step whose end passed `staleAfter` ago with no word from the phone. Paused and
    /// completed snapshots are never stale: a pause can rightly last a long time.
    func isStale(at date: Date) -> Bool {
        guard !isPaused, !isCompleted, let stepEnd else { return false }
        return date > stepEnd.addingTimeInterval(Self.staleAfter)
    }

    #if DEBUG
    /// A routine part-way through, for previews and `-demoSnapshot` launches.
    static func demo(paused: Bool = false, completed: Bool = false, now: Date = .now) -> WatchSnapshot {
        var snapshot = WatchSnapshot([
            "active": true, "routineTitle": "Morning Calm", "stepTitle": "Box breathing",
            "stepIndex": 1, "stepCount": 4, "nextStepTitle": "Body scan", "stepDuration": 120.0,
            "remaining": 75.0, "isPaused": paused, "isCompleted": completed
        ])!
        if !paused && !completed { snapshot.stepEnd = now.addingTimeInterval(75) }
        return snapshot
    }
    #endif

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

/// A control button, sent to the phone as `["command": rawValue]` (WatchSessionManager.swift).
enum WatchCommand: String {
    case pause, resume, skip, end
}

@MainActor
final class WatchSessionModel: NSObject, ObservableObject, WCSessionDelegate {
    @Published private(set) var snapshot: WatchSnapshot?
    /// The command waiting for the phone's answer; the buttons are disabled meanwhile.
    @Published private(set) var pendingCommand: WatchCommand?
    /// A short message after a command failed, cleared after a few seconds.
    @Published private(set) var feedback: LocalizedStringResource?
    private var feedbackTask: Task<Void, Never>?

    override init() {
        super.init()
        #if DEBUG
        // Screenshots / UI checks without a paired phone: `-demoSnapshot` (running) or
        // `-demoSnapshotPaused`.
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-demoSnapshot") || arguments.contains("-demoSnapshotPaused") {
            snapshot = .demo(paused: arguments.contains("-demoSnapshotPaused"))
            return
        }
        #endif
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Sends a control to the phone, which wakes the iPhone app in the background if need be. The
    /// screen isn't changed until the phone sends the resulting state, except after End, which can
    /// only end the routine.
    func send(_ command: WatchCommand) {
        guard pendingCommand == nil else { return }
        let session = WCSession.default
        guard WCSession.isSupported(), session.activationState == .activated, session.isReachable else {
            fail(with: "iPhone not reachable")
            return
        }
        pendingCommand = command
        session.sendMessage(
            ["command": command.rawValue],
            replyHandler: { reply in
                let ok = reply["ok"] as? Bool ?? false
                Task { @MainActor in self.finish(command, ok: ok) }
            },
            errorHandler: { _ in
                Task { @MainActor in
                    self.pendingCommand = nil
                    self.fail(with: "iPhone not reachable")
                }
            }
        )
    }

    private func finish(_ command: WatchCommand, ok: Bool) {
        pendingCommand = nil
        if ok {
            // The phone's `active: false` follows; clearing now is safe as End can't be undone.
            if command == .end { snapshot = nil }
        } else {
            // The phone had no routine running (it ended there, or the app was relaunched).
            snapshot = nil
            fail(with: "No routine running on iPhone")
        }
    }

    private func fail(with message: LocalizedStringResource) {
        WKInterfaceDevice.current().play(.failure)
        feedback = message
        feedbackTask?.cancel()
        feedbackTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            self.feedback = nil
        }
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
