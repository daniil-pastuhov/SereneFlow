import SwiftUI
import WidgetKit

/// Home Screen widget that starts a routine with one tap, through `sereneflow://start` links.
///
/// The app writes the routines to offer (last started first) to `quickstart.json` in the App Group
/// container (composeApp QuickStart.ios.kt). Until both targets have the App Group entitlement
/// there is no container; the widget then offers "your last routine", which the app resolves.
struct QuickStartWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuickStart", provider: QuickStartProvider()) { entry in
            QuickStartView(entry: entry)
        }
        .configurationDisplayName("Quick Start")
        .description("Start a routine in one tap.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

private let appGroup = "group.com.sereneflow.timer"
private let quickStartFile = "quickstart.json"
private let widgetAccent = Color(red: 0.44, green: 0.66, blue: 0.54)

struct QuickStartRoutine: Codable, Hashable {
    let id: String
    let title: String
    let subtitle: String
}

struct QuickStartEntry: TimelineEntry {
    let date: Date
    let routines: [QuickStartRoutine]
}

struct QuickStartProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickStartEntry {
        // A sample of what the widget shows, in the gallery and while it loads.
        QuickStartEntry(date: Date(), routines: [
            QuickStartRoutine(
                id: "last",
                title: String(localized: "Morning Awakening"),
                subtitle: String(localized: "4 steps · 15 min")
            )
        ])
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickStartEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : QuickStartEntry(date: Date(), routines: loadRoutines()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickStartEntry>) -> Void) {
        // The app reloads the widget when it goes to the background; this is only a fallback.
        let entry = QuickStartEntry(date: Date(), routines: loadRoutines())
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(6 * 60 * 60))))
    }

    private func loadRoutines() -> [QuickStartRoutine] {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup),
              let data = try? Data(contentsOf: container.appendingPathComponent(quickStartFile)),
              let routines = try? JSONDecoder().decode([QuickStartRoutine].self, from: data)
        else { return [] }
        return routines
    }
}

private func startURL(_ routineId: String) -> URL {
    var components = URLComponents()
    components.scheme = "sereneflow"
    components.host = "start"
    components.queryItems = [URLQueryItem(name: "routine", value: routineId)]
    return components.url ?? URL(string: "sereneflow://start")!
}

struct QuickStartView: View {
    @Environment(\.widgetFamily) private var family
    let entry: QuickStartEntry

    var body: some View {
        content
            .widgetBackground(Color(red: 0.95, green: 0.97, blue: 0.95))
    }

    @ViewBuilder
    private var content: some View {
        if family == .systemMedium, entry.routines.count > 1 {
            VStack(alignment: .leading, spacing: 6) {
                Label("Quick Start", systemImage: "leaf.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(widgetAccent)
                ForEach(entry.routines.prefix(3), id: \.self) { routine in
                    Link(destination: startURL(routine.id)) {
                        RoutineRow(routine: routine)
                    }
                }
                Spacer(minLength: 0)
            }
        } else {
            let routine = entry.routines.first
            VStack(alignment: .leading, spacing: 4) {
                Image(systemName: "play.circle.fill")
                    .font(.title)
                    .foregroundStyle(widgetAccent)
                    .accessibilityHidden(true)
                Spacer(minLength: 0)
                Text(routine?.title ?? String(localized: "Last routine"))
                    .font(.headline)
                    .foregroundStyle(.black)
                    .lineLimit(2)
                Text(routine?.subtitle ?? String(localized: "Tap to start"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .widgetURL(startURL(routine?.id ?? "last"))
            .accessibilityElement(children: .combine)
            .accessibilityLabel(
                routine.map { String(localized: "Start \($0.title)") } ?? String(localized: "Start your last routine")
            )
        }
    }
}

private struct RoutineRow: View {
    let routine: QuickStartRoutine

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "play.fill")
                .font(.caption)
                .foregroundStyle(widgetAccent)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(routine.title).font(.subheadline.weight(.medium)).foregroundStyle(.black).lineLimit(1)
                Text(routine.subtitle).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Start \(routine.title)")
    }
}

private extension View {
    /// iOS 17 requires widgets to declare their background this way; earlier systems don't have it.
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            padding().background(color)
        }
    }
}
