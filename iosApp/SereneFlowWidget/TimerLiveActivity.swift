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
                // "3/5" and a decorative leaf; VoiceOver hears "Step 3 of 5".
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(state.isCompleted ? String(localized: "Done") : stepPositionLabel(for: state))
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
                .accessibilityLabel(state.isPaused ? "Paused" : "Running")
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
                Text(state.isCompleted ? String(localized: "Routine complete") : state.stepTitle)
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                CountdownText(state: state)
                    .font(.title3.weight(.light).monospacedDigit())
                    .foregroundStyle(accent)
                if !state.isCompleted {
                    Text(state.rounds > 1
                         ? "Round \(state.round)/\(state.rounds) · \(state.stepIndex + 1)/\(state.stepCount)"
                         : "Step \(state.stepIndex + 1)/\(state.stepCount)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .accessibilityLabel(stepPositionLabel(for: state))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
    }
}

/// "Step 3 of 5".
private func stepLabel(for state: TimerActivityAttributes.ContentState) -> String {
    String(localized: "Step \(state.stepIndex + 1) of \(state.stepCount)")
}

/// "Round 2 of 4, Step 3 of 5" for VoiceOver, where the layout shows "2/4 · 3/5".
private func stepPositionLabel(for state: TimerActivityAttributes.ContentState) -> String {
    guard state.rounds > 1 else { return stepLabel(for: state) }
    return String(localized: "Round \(state.round) of \(state.rounds), \(stepLabel(for: state))")
}

private func subtitle(for state: TimerActivityAttributes.ContentState) -> String {
    if state.isCompleted { return String(localized: "Routine complete. Well done.") }
    if state.isPaused { return String(localized: "Paused") }
    if let next = state.nextStepTitle { return String(localized: "Up next: \(next)") }
    return String(localized: "Final step")
}

/// The Lock Screen's line under the step: "Round 2 of 4 · Step 3 of 5 · Up next: Rest".
private func lockScreenStatus(for state: TimerActivityAttributes.ContentState) -> String {
    let position = state.rounds > 1
        ? String(localized: "Round \(state.round) of \(state.rounds) · \(stepLabel(for: state))")
        : stepLabel(for: state)
    return String(localized: "\(position) · \(subtitle(for: state))")
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
                    Text(state.isCompleted ? String(localized: "Routine complete") : state.stepTitle)
                        .font(.headline)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(state.isCompleted ? String(localized: "Well done.") : lockScreenStatus(for: state))
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                        .lineLimit(1)
                }
                Spacer(minLength: 12)
                CountdownText(state: state)
                    .font(.largeTitle.weight(.light).monospacedDigit())
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120, alignment: .trailing)
            }
            StepProgressBar(state: state)
            if !state.isCompleted {
                ControlButtons(state: state)
            }
        }
        .padding(16)
    }
}

/// Pause/Resume, Skip and End, run in the app's process by LiveActivityIntents (iOS 17+).
private struct ControlButtons: View {
    @Environment(\.routineAccent) private var accent
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        if #available(iOS 17.0, *) {
            HStack(spacing: 10) {
                Button(intent: ToggleTimerIntent()) {
                    Label(state.isPaused ? "Resume" : "Pause", systemImage: state.isPaused ? "play.fill" : "pause.fill")
                }
                Button(intent: SkipStepIntent()) {
                    Label("Skip", systemImage: "forward.end.fill")
                }
                Button(intent: EndRoutineIntent()) {
                    Label("End", systemImage: "xmark")
                }
            }
            .font(.caption.weight(.semibold))
            .buttonStyle(.bordered)
            .tint(accent)
        }
    }
}

/// Live countdown while running; a fixed value while paused. VoiceOver hears "Time left" first.
private struct CountdownText: View {
    let state: TimerActivityAttributes.ContentState

    var body: some View {
        if state.isCompleted {
            Text("Done")
        } else {
            time.accessibilityLabel(Text("Time left \(time)"))
        }
    }

    private var time: Text {
        if let remaining = state.pausedRemaining {
            return Text(format(remaining))
        }
        return Text(timerInterval: state.stepStart...max(state.stepStart, state.stepEnd), countsDown: true)
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
        .accessibilityLabel("Step progress")
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
        .accessibilityLabel("Step progress")
    }
}

private func pausedFraction(_ remaining: TimeInterval, of duration: TimeInterval) -> Double {
    guard duration > 0 else { return 1 }
    return min(max(1 - remaining / duration, 0), 1)
}
