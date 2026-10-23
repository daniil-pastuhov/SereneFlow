package com.sereneflow.timer.platform

import com.sereneflow.timer.repository.Repositories
import com.sereneflow.timer.storage.StorageJson
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL

/** [UIApplicationShortcutItem.type] of the quick actions; the Swift SceneDelegate reads it. */
const val START_ROUTINE_SHORTCUT = "com.sereneflow.timer.start"

/** userInfo key holding the routine id. */
const val SHORTCUT_ROUTINE_ID = "routineId"

/**
 * Shared with the widget extension (iosApp/SereneFlowWidget/QuickStartWidget.swift). Only usable
 * once both targets have this App Group entitlement; until then the widget offers "last routine".
 */
const val APP_GROUP = "group.com.sereneflow.timer"
const val QUICK_START_FILE = "quickstart.json"

private var publishedItems: List<QuickStartItem> = emptyList()

/** The routines to offer as Home Screen quick actions; read by QuickActions.swift. */
fun currentQuickStartItems(): List<QuickStartItem> = publishedItems

/** Every routine that can be started, for Siri and Shortcuts (RoutineIntents.swift). */
fun allRoutineSummaries(): List<QuickStartItem> =
    quickStartItems(Repositories.routines.routines.value, lastRoutineId = null, limit = Int.MAX_VALUE)

@Serializable
private data class WidgetRoutine(val id: String, val title: String, val subtitle: String)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun publishQuickStart(items: List<QuickStartItem>) {
    // UIApplication.shortcutItems is missing from the Kotlin/Native bindings: the Swift scene
    // delegate sets the quick actions from these as the app goes to the background.
    publishedItems = items

    // The widget's list. Without the App Group entitlement there is no container: nothing to do.
    val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP) ?: return
    val url = container.URLByAppendingPathComponent(QUICK_START_FILE) ?: return
    val json = StorageJson.encodeToString(items.map { WidgetRoutine(it.routineId, it.title, it.subtitle) })
    NSString.create(string = json).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
}
