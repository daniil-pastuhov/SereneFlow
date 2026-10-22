import ActivityKit
import SwiftUI
import WidgetKit

private let accent = Color(red: 0.44, green: 0.66, blue: 0.54)

/// Lock Screen banner and Dynamic Island for a running routine. Countdowns and progress use
/// `timerInterval` views, which the system animates on its own, so they stay live between updates.
struct TimerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TimerActivityAttributes.self) { context in
            LockScreenView(routineTitle: context.attributes.routineTitle, state: context.state)
                .activityBackgroundTint(Color.black.opacity(0.35))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            let state = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(state.isCompleted ? "Done" : "\(state.stepIndex + 1)/\(state.stepCount)")
                    } icon: {
                        Image(systemName: "leaf.fill")
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(accent)
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    CountdownText(state: state)
                        .font(.title2.weight(.light).monospacedDigit())
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 90, alignment: .trailing)
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(state.isCompleted ? context.attributes.routineTitle : state.stepTitle)
                        .font(.headline)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        StepProgressBar(state: state)
                        Text(subtitle(for: state))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Image(systemName: state.isPaused ? "pause.fill" : "leaf.fill")
                    .foregroundStyle(accent)
            } compactTrailing: {
                CountdownText(state: state)
                    .font(.caption.monospacedDigit())
                    .multilineTextAlignment(.trailing)
                    .frame(width: 44)
                    .foregroundStyle(accent)
            } minimal: {
                StepProgressRing(state: state)
            }
            .keylineTint(accent)
        }
    }
}

private func subtitle(for state: TimerActivityAttributes.ContentState) -> String {
    if state.isCompleted { return "Routine complete. Well done." }
    if state.isPaused { return "Paused" }
    if let next = state.nextStepTitle { return "Up next: \(next)" }
    return "Final step"
}

private struct LockScreenView: View {
    let routineTitle: String
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(routineTitle.uppercased())
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(accent)
                    Text(state.isCompleted ? "Routine complete" : state.stepTitle)
                        .font(.headline)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(state.isCompleted ? "Well done." : "Step \(state.stepIndex + 1) of \(state.stepCount) · \(subtitle(for: state))")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                        .lineLimit(1)
                }
                Spacer(minLength: 12)
                CountdownText(state: state)
                    .font(.system(size: 36, weight: .light).monospacedDigit())
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120, alignment: .trailing)
            }
            StepProgressBar(state: state)
        }
        .padding(16)
    }
}

/// Live countdown while running; a fixed value while paused.
private struct CountdownText: View {
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        if state.isCompleted {
            Text("Done")
        } else if let remaining = state.pausedRemaining {
            Text(format(remaining))
        } else {
            Text(timerInterval: state.stepStart...max(state.stepStart, state.stepEnd), countsDown: true)
        }
    }

    private func format(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded(.up))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

private struct StepProgressBar: View {
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        Group {
            if state.isCompleted {
                ProgressView(value: 1)
            } else if let remaining = state.pausedRemaining {
                ProgressView(value: pausedFraction(remaining, of: state.stepDuration))
            } else {
                ProgressView(timerInterval: state.stepStart...max(state.stepStart, state.stepEnd), countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
            }
        }
        .progressViewStyle(.linear)
        .tint(accent)
    }
}

private struct StepProgressRing: View {
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        Group {
            if state.isCompleted {
                ProgressView(value: 1)
            } else if let remaining = state.pausedRemaining {
                ProgressView(value: pausedFraction(remaining, of: state.stepDuration))
            } else {
                ProgressView(timerInterval: state.stepStart...max(state.stepStart, state.stepEnd), countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
            }
        }
        .progressViewStyle(.circular)
        .tint(accent)
    }
}

private func pausedFraction(_ remaining: TimeInterval, of duration: TimeInterval) -> Double {
    guard duration > 0 else { return 1 }
    return min(max(1 - remaining / duration, 0), 1)
}
