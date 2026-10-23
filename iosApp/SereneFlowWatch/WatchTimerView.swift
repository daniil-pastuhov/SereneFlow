import SwiftUI
import WatchKit

private let accent = Color(red: 0.44, green: 0.66, blue: 0.54)

struct WatchTimerView: View {
    @ObservedObject var model: WatchSessionModel

    var body: some View {
        // The stack hosts RunningView's bottom toolbar (the controls).
        NavigationStack {
            // Re-checked on a timer, so a stale snapshot clears without a new message from the phone.
            TimelineView(.periodic(from: .now, by: 5)) { context in
                if let snapshot = model.snapshot, !snapshot.isStale(at: context.date) {
                    RunningView(snapshot: snapshot, pendingCommand: model.pendingCommand, send: model.send)
                } else {
                    EmptyStateView()
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let feedback = model.feedback {
                FeedbackBanner(message: feedback)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.feedback)
    }
}

/// Why a control didn't work, e.g. "iPhone not reachable"; over the controls for a moment.
private struct FeedbackBanner: View {
    let message: LocalizedStringResource

    var body: some View {
        Text(message)
            .font(.footnote.weight(.semibold))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.regularMaterial, in: Capsule())
            .padding(.horizontal, 8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .onAppear {
                AccessibilityNotification.Announcement(String(localized: message)).post()
            }
    }
}

private struct EmptyStateView: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "leaf.fill")
                .font(.title2)
                .foregroundStyle(accent)
                .accessibilityHidden(true)
            Text("No routine running")
                .font(.headline)
            Text("Start one on your iPhone to follow it here.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

private struct RunningView: View {
    let snapshot: WatchSnapshot
    var pendingCommand: WatchCommand? = nil
    var send: (WatchCommand) -> Void = { _ in }

    /// The Live Activity's controls; gone once the routine is complete, as there.
    private var showsControls: Bool { !snapshot.isCompleted }

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                ProgressRing(snapshot: snapshot)
                    // A little smaller with the controls below, so the step title still fits.
                    .frame(width: showsControls ? 84 : 92, height: showsControls ? 84 : 92)
                Countdown(snapshot: snapshot)
                    .font(.title3.weight(.light).monospacedDigit())
            }
            Text(snapshot.isCompleted ? String(localized: "Routine complete") : snapshot.stepTitle)
                .font(.headline)
                .lineLimit(showsControls ? 1 : 2)
                .minimumScaleFactor(0.8)
                .multilineTextAlignment(.center)
            Text(statusLine)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 4)
        .toolbar {
            if showsControls {
                ToolbarItemGroup(placement: .bottomBar) {
                    ControlButtons(isPaused: snapshot.isPaused, pendingCommand: pendingCommand, send: send)
                }
            }
        }
    }

    private var statusLine: String {
        if snapshot.isCompleted { return snapshot.routineTitle }
        if snapshot.isPaused { return String(localized: "Paused") }
        let step = String(localized: "Step \(snapshot.stepIndex + 1) of \(snapshot.stepCount)")
        guard snapshot.rounds > 1 else { return step }
        return String(localized: "Round \(snapshot.round) of \(snapshot.rounds) · \(step)")
    }
}

/// End, Pause/Resume (in the middle, as the main one) and Skip: the Live Activity's controls, with
/// its labels and symbols. Pause/Resume is sent as which one it shows, not as a toggle, so a tap on
/// a screen that is behind the phone can't do the opposite (see WatchSessionManager.swift).
private struct ControlButtons: View {
    let isPaused: Bool
    let pendingCommand: WatchCommand?
    let send: (WatchCommand) -> Void

    var body: some View {
        Button(role: .destructive) {
            tap(.end)
        } label: {
            Label("End", systemImage: "xmark")
        }
        .accessibilityLabel(Text("End routine"))
        .disabled(pendingCommand != nil)
        Spacer()
        Button {
            tap(isPaused ? .resume : .pause)
        } label: {
            Label(isPaused ? "Resume" : "Pause", systemImage: isPaused ? "play.fill" : "pause.fill")
        }
        .buttonStyle(.borderedProminent)
        .tint(accent)
        .accessibilityLabel(isPaused ? Text("Resume routine") : Text("Pause routine"))
        .disabled(pendingCommand != nil)
        Spacer()
        Button {
            tap(.skip)
        } label: {
            Label("Skip", systemImage: "forward.fill")
        }
        .accessibilityLabel(Text("Skip to next step"))
        .disabled(pendingCommand != nil)
    }

    private func tap(_ command: WatchCommand) {
        guard pendingCommand == nil else { return }
        WKInterfaceDevice.current().play(.click)
        send(command)
    }
}

/// The step's time left; VoiceOver hears "Time left" first.
private struct Countdown: View {
    let snapshot: WatchSnapshot

    var body: some View {
        if snapshot.isCompleted {
            Text("Done")
        } else {
            time
                .multilineTextAlignment(.center)
                .accessibilityLabel(Text("Time left \(time)"))
        }
    }

    private var time: Text {
        if let end = snapshot.stepEnd, !snapshot.isPaused {
            let start = end.addingTimeInterval(-snapshot.stepDuration)
            return Text(timerInterval: start...max(start, end), countsDown: true)
        }
        let total = Int(snapshot.remaining.rounded(.up))
        return Text(String(format: "%d:%02d", total / 60, total % 60))
    }
}

private struct ProgressRing: View {
    let snapshot: WatchSnapshot

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let fraction = progress(at: context.date)
            ZStack {
                Circle().stroke(accent.opacity(0.25), lineWidth: 8)
                Circle()
                    .trim(from: 0, to: fraction)
                    .stroke(accent, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            .accessibilityElement()
            .accessibilityLabel("Step progress")
            .accessibilityValue("\(Int((fraction * 100).rounded())) percent")
        }
    }

    private func progress(at date: Date) -> Double {
        if snapshot.isCompleted { return 1 }
        guard snapshot.stepDuration > 0 else { return 1 }
        let remaining = snapshot.stepEnd.map { $0.timeIntervalSince(date) } ?? snapshot.remaining
        return min(max(1 - remaining / snapshot.stepDuration, 0), 1)
    }
}

enum WKInterfaceDeviceHaptics {
    static func play(completed: Bool) {
        WKInterfaceDevice.current().play(completed ? .success : .notification)
    }
}

#if DEBUG
#Preview("Running") {
    NavigationStack { RunningView(snapshot: .demo()) }
}

#Preview("Paused") {
    NavigationStack { RunningView(snapshot: .demo(paused: true)) }
}

#Preview("Complete") {
    NavigationStack { RunningView(snapshot: .demo(completed: true)) }
}
#endif
