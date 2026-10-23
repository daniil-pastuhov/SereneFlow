import AppIntents
import Foundation

/// What the timer controls do. The app sets it at launch (iosApp.swift) to call the shared Kotlin
/// session; the widget extension compiles these intents too (for the Live Activity's buttons) but
/// leaves it unset, as a LiveActivityIntent's perform() runs in the app's process.
enum TimerControl {
    case toggle, skip, end

    /// Set once at launch, always called on the main thread.
    static var handler: ((TimerControl) -> Bool)?
}

private func run(_ control: TimerControl) async -> Bool {
    await MainActor.run { TimerControl.handler?(control) ?? false }
}

/// Pause or resume the running routine. A Live Activity button on iOS 17+; a Siri phrase too.
@available(iOS 17.0, *)
struct ToggleTimerIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Pause or Resume Routine"
    static var description = IntentDescription("Pauses the running SereneFlow routine, or resumes it.")

    func perform() async throws -> some IntentResult {
        _ = await run(.toggle)
        return .result()
    }
}

/// Move on to the next step of the running routine.
@available(iOS 17.0, *)
struct SkipStepIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Skip Step"
    static var description = IntentDescription("Moves the running SereneFlow routine on to its next step.")

    func perform() async throws -> some IntentResult {
        _ = await run(.skip)
        return .result()
    }
}

/// End the running routine.
@available(iOS 17.0, *)
struct EndRoutineIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "End Routine"
    static var description = IntentDescription("Ends the running SereneFlow routine.")

    func perform() async throws -> some IntentResult {
        _ = await run(.end)
        return .result()
    }
}
