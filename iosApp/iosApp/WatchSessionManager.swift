import ComposeApp
import Foundation
import WatchConnectivity

/// Swift side of the Kotlin `WatchBridge`: sends the running routine to the Apple Watch app as
/// WatchConnectivity application context (the latest state wins, delivered when the watch app
/// next runs). Keys are read by SereneFlowWatch/SereneFlowWatchApp.swift.
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
        roundLabel: String?
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
            // Application context only sends changes; this makes each update distinct.
            "sentAt": Date().timeIntervalSince1970
        ]
        if let nextStepTitle { context["nextStepTitle"] = nextStepTitle }
        if let roundLabel { context["roundLabel"] = roundLabel }
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

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        // A different watch was paired: activate again for it.
        session.activate()
    }
}
