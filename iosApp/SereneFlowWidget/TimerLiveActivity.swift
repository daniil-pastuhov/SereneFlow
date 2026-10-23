import ActivityKit
import SwiftUI
import WidgetKit

private let defaultAccent = Color(red: 0.44, green: 0.66, blue: 0.54)

private struct RoutineAccentKey: EnvironmentKey {
    static let defaultValue = defaultAccent
}

extension EnvironmentValues {
    /// The running routine's accent colour, set at the root of each activity view.
    fileprivate var routineAccent: Color {
        get { self[RoutineAccentKey.self] }
        set { self[RoutineAccentKey.self] = newValue }
    }
}

extension TimerActivityAttributes {
    /// The routine's own colour, or the app's when it follows the theme.
    fileprivate var accent: Color {
        guard let argb = accentArgb, argb != 0 else { return defaultAccent }
        return Color(
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255
        )
    }
}

/// Lock Screen banner and Dynamic Island for a running routine. Countdowns and progress use
/// `timerInterval` views, which the system animates on its own, so they stay live between updates.
///
/// On iOS 18+ it is also offered to a paired Apple Watch's Smart Stack, where the `.small` family
/// shows a compact layout instead of the Lock Screen banner.
struct TimerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        withWatchSmartStack(timerActivityConfiguration())
    }

    // An opaque result type may differ per `#available` branch (SE-0360).
    private func withWatchSmartStack(
        _ configuration: ActivityConfiguration<TimerActivityAttributes>
    ) -> some WidgetConfiguration {
        if #available(iOS 18.0, *) {
            return configuration.supplementalActivityFamilies([.small])
        } else {
            return configuration
        }
    }
}

private func timerActivityConfiguration() -> ActivityConfiguration<TimerActivityAttributes> {
    ActivityConfiguration(for: TimerActivityAttributes.self) { context in
        ActivityContentView(routineTitle: context.attributes.routineTitle, state: context.state)
            .environment(\.routineAccent, context.attributes.accent)
            .activityBackgroundTint(Color.black.opacity(0.35))
            .activitySystemActionForegroundColor(.white)
    } dynamicIsland: { context in
        let state = context.state
        let accent = context.attributes.accent
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
                .environment(\.routineAccent, accent)
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
                .environment(\.routineAccent, accent)
        }
        .keylineTint(accent)
    }
}

/// The Lock Screen banner, or on iOS 18+ whichever layout the activity family asks for.
private struct ActivityContentView: View {
    let routineTitle: String
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        if #available(iOS 18.0, *) {
            FamilyAdaptiveView(routineTitle: routineTitle, state: state)
        } else {
            LockScreenView(routineTitle: routineTitle, state: state)
        }
    }
}

/// The Lock Screen banner on the phone; a compact layout in the watch's Smart Stack.
@available(iOS 18.0, *)
private struct FamilyAdaptiveView: View {
    @Environment(\.activityFamily) private var family
    let routineTitle: String
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        switch family {
        case .small:
            WatchSmallView(state: state)
        default:
            LockScreenView(routineTitle: routineTitle, state: state)
        }
    }
}

/// Apple Watch Smart Stack: step name, live countdown, step X/Y and a progress ring.
private struct WatchSmallView: View {
    @Environment(\.routineAccent) private var accent
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: 8) {
            StepProgressRing(state: state)
                .frame(width: 36, height: 36)
                .overlay {
                    Image(systemName: state.isPaused ? "pause.fill" : "leaf.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(accent)
                        .accessibilityLabel(state.isPaused ? "Paused" : "Running")
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(state.isCompleted ? "Routine complete" : state.stepTitle)
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                CountdownText(state: state)
                    .font(.title3.weight(.light).monospacedDigit())
                    .foregroundStyle(accent)
                if !state.isCompleted {
                    Text("Step \(state.stepIndex + 1)/\(state.stepCount)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("Step \(state.stepIndex + 1) of \(state.stepCount)")
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
    }
}

private func subtitle(for state: TimerActivityAttributes.ContentState) -> String {
    if state.isCompleted { return "Routine complete. Well done." }
    if state.isPaused { return "Paused" }
    if let next = state.nextStepTitle { return "Up next: \(next)" }
    return "Final step"
}

private struct LockScreenView: View {
    @Environment(\.routineAccent) private var accent
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
    @Environment(\.routineAccent) private var accent
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
    @Environment(\.routineAccent) private var accent
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
