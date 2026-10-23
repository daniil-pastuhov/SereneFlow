import AppIntents
import ComposeApp
import Foundation

/// A saved routine, as Siri and Shortcuts see it. Read from the shared Kotlin repository.
struct RoutineEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Routine"
    static var defaultQuery = RoutineQuery()

    let id: String
    let title: String
    let subtitle: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)", subtitle: "\(subtitle)")
    }
}

struct RoutineQuery: EntityStringQuery {
    @MainActor
    private func all() -> [RoutineEntity] {
        QuickStart_iosKt.allRoutineSummaries().map {
            RoutineEntity(id: $0.routineId, title: $0.title, subtitle: $0.subtitle)
        }
    }

    func entities(for identifiers: [String]) async throws -> [RoutineEntity] {
        await all().filter { identifiers.contains($0.id) }
    }

    func entities(matching string: String) async throws -> [RoutineEntity] {
        await all().filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    func suggestedEntities() async throws -> [RoutineEntity] {
        await all()
    }
}

/// Opens SereneFlow and starts a routine ("Start Morning Awakening in SereneFlow").
struct StartRoutineIntent: AppIntent {
    static var title: LocalizedStringResource = "Start Routine"
    static var description = IntentDescription("Opens SereneFlow and starts one of your routines.")
    // The timer runs in the app, with its screen, sound and Live Activity.
    static var openAppWhenRun = true

    @Parameter(title: "Routine")
    var routine: RoutineEntity

    @MainActor
    func perform() async throws -> some IntentResult {
        MainViewControllerKt.startRoutineFromShortcut(routineId: routine.id)
        return .result()
    }
}

/// Siri phrases. Phrases must name the app; the routine phrase lists the user's routines.
@available(iOS 17.0, *)
struct SereneFlowShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: StartRoutineIntent(),
            phrases: [
                "Start \(\.$routine) in \(.applicationName)",
                "Start \(\.$routine) with \(.applicationName)",
                "Start a routine in \(.applicationName)"
            ],
            shortTitle: "Start Routine",
            systemImageName: "play.fill"
        )
        AppShortcut(
            intent: ToggleTimerIntent(),
            phrases: ["Pause \(.applicationName)", "Resume \(.applicationName)"],
            shortTitle: "Pause or Resume",
            systemImageName: "playpause.fill"
        )
        AppShortcut(
            intent: SkipStepIntent(),
            phrases: ["Skip step in \(.applicationName)", "Next step in \(.applicationName)"],
            shortTitle: "Skip Step",
            systemImageName: "forward.fill"
        )
        AppShortcut(
            intent: EndRoutineIntent(),
            phrases: ["End routine in \(.applicationName)", "Stop \(.applicationName)"],
            shortTitle: "End Routine",
            systemImageName: "stop.fill"
        )
    }
}
