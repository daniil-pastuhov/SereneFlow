package com.sereneflow.timer.platform

import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sereneflow.timer.ExternalAction

/** The activity in androidApp that reads `sereneflow://start` links (by name: it's another module). */
private const val ACTIVITY_CLASS = "com.sereneflow.timer.SereneFlowActivity"

actual fun publishQuickStart(items: List<QuickStartItem>) {
    val context = AndroidAppContext.appContext ?: return
    try {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val shortcuts = items.take(max).mapIndexed { rank, item ->
            ShortcutInfoCompat.Builder(context, "start_${item.routineId}")
                .setShortLabel(item.title.take(25))
                .setLongLabel("Start ${item.title}".take(45))
                .setIcon(IconCompat.createWithResource(context, context.applicationInfo.icon))
                .setRank(rank)
                .setIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse(ExternalAction.startUrl(item.routineId)))
                        .setClassName(context.packageName, ACTIVITY_CLASS)
                )
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    } catch (_: Exception) {
        // Rate-limited or unsupported launcher: the shortcuts are a convenience.
    }
}
