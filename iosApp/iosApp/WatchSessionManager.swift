import ComposeApp
import Foundation
import WatchConnectivity

/// Swift side of the Kotlin `WatchBridge`: sends the running routine to the Apple Watch app as
/// WatchConnectivity application context (the latest state wins, delivered when the watch app
/// next runs). Keys are read by SereneFlowWatch/SereneFlowWatchApp.swift. It also takes the watch
/// app's Pause/Resume, Skip and End buttons, sent as messages (see `didReceiveMessage`).
final class WatchSessionManager: NSObject, WatchBridge, WCSessionDelegate {
    private var pending: [String: Any]?

    override init() {
        super.init()
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func update(
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
        round: Int32,
        rounds: Int32
    ) {
        var context: [String: Any] = [
            "active": true,
            "routineTitle": routineTitle,
            "stepTitle": stepTitle,
            "stepIndex": Int(stepIndex),
            "stepCount": Int(stepCount),
            "stepDuration": stepDurationSeconds,
            "remaining": remainingSeconds,
            "isPaused": isPaused,
            "isCompleted": isCompleted,
            "round": Int(round),
            "rounds": Int(rounds),
            // Application context only sends changes; this makes each update distinct.
            "sentAt": Date().timeIntervalSince1970
        ]
        if let nextStepTitle { context["nextStepTitle"] = nextStepTitle }
        if !isPaused && !isCompleted { context["stepEnd"] = stepEndEpochSeconds }
        send(context)
    }

    func end() {
        send(["active": false, "sentAt": Date().timeIntervalSince1970])
    }

    private func send(_ context: [String: Any]) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated else {
            pending = context // sent once activation completes
            return
        }
        guard session.isPaired, session.isWatchAppInstalled else { return }
        try? session.updateApplicationContext(context)
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        DispatchQueue.main.async {
            if let pending = self.pending {
                self.pending = nil
                self.send(pending)
            }
        }
    }

    /// A control from the watch: `["command": "pause" | "resume" | "skip" | "end"]`, answered with
    /// `["ok": Bool]` (false when no routine is running or the command is unknown). It runs the same
    /// session calls as the Live Activity's buttons (TimerControl.handler, set in iosApp.swift); the
    /// new state then reaches the watch through `update` / `end` as usual. The watch sends pause and
    /// resume rather than a toggle, so a repeated tap, or one made on a stale screen, can't flip the
    /// state back.
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        let control: TimerControl? = switch message["command"] as? String {
        case "pause": .pause
        case "resume": .resume
        case "skip": .skip
        case "end": .end
        default: nil
        }
        DispatchQueue.main.async {
            let ok = control.flatMap { TimerControl.handler?($0) } ?? false
            replyHandler(["ok": ok])
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        // A different watch was paired: activate again for it.
        session.activate()
    }
}
