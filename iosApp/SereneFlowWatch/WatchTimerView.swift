import SwiftUI
import WatchKit

private let accent = Color(red: 0.44, green: 0.66, blue: 0.54)

struct WatchTimerView: View {
    @ObservedObject var model: WatchSessionModel

    var body: some View {
        if let snapshot = model.snapshot {
            RunningView(snapshot: snapshot)
        } else {
            VStack(spacing: 8) {
                Image(systemName: "leaf.fill")
                    .font(.title2)
                    .foregroundStyle(accent)
                    .accessibilityHidden(true)
                Text("No routine running")
                    .font(.headline)
                Text("Start one on your iPhone, with Follow routine with screen off turned on.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}

private struct RunningView: View {
    let snapshot: WatchSnapshot

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                ProgressRing(snapshot: snapshot)
                    .frame(width: 92, height: 92)
                Countdown(snapshot: snapshot)
                    .font(.title3.weight(.light).monospacedDigit())
            }
            Text(snapshot.isCompleted ? String(localized: "Routine complete") : snapshot.stepTitle)
                .font(.headline)
                .lineLimit(2)
                .multilineTextAlignment(.center)
            Text(statusLine)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 4)
    }

    private var statusLine: String {
        if snapshot.isCompleted { return snapshot.routineTitle }
        if snapshot.isPaused { return String(localized: "Paused") }
        let step = String(localized: "Step \(snapshot.stepIndex + 1) of \(snapshot.stepCount)")
        guard snapshot.rounds > 1 else { return step }
        return String(localized: "Round \(snapshot.round) of \(snapshot.rounds) · \(step)")
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
