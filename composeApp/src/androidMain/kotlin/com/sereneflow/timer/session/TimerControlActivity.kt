package com.sereneflow.timer.session

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sereneflow.timer.ExternalAction

/**
 * Google Assistant's pause / resume / skip / end commands (androidApp res/xml/shortcuts.xml): a
 * `sereneflow://pause`-style link, carried out on the running [TimerSession] like the
 * notification's buttons (TimerActionReceiver), without bringing the app forward. App Actions can
 * only start an activity, so this one has no window (Theme.NoDisplay), its own task and no history,
 * and finishes at once. With no routine running it opens the app instead, so the request isn't
 * met with nothing at all.
 */
class TimerControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.dataString?.let(ExternalAction::parseUrl) as? ExternalAction.ControlTimer
        val handled = action != null && TimerSession.control(action.command)
        if (!handled) {
            packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }
}
