package com.sereneflow.timer

import android.content.Intent
import android.content.pm.ApplicationInfo
import com.sereneflow.timer.transfer.readText
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sereneflow.timer.platform.AndroidAppContext
import com.sereneflow.timer.platform.AppIconSwitcher

/**
 * The app's only activity. The launcher reaches it through the per-icon aliases in the manifest;
 * the default one keeps the name `.MainActivity`, which home-screen shortcuts from 1.0 point at.
 */
class SereneFlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.appContext = applicationContext
        enableEdgeToEdge()
        // Only on a fresh start, so a rotation doesn't restart the demo routine.
        val demoLaunch = if (savedInstanceState == null) demoLaunchFromIntent() else null
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            App(demoLaunch = demoLaunch)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * A `sereneflow://start` link (launcher shortcut) starts its routine; a routine file opened
     * with the app is offered for import. [App] carries out both.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == ExternalAction.URL_SCHEME) {
            ExternalAction.parseUrl(uri.toString())?.let(ExternalActions::post)
            return
        }
        readText(this, uri)?.let { ExternalActions.post(ExternalAction.ImportRoutines(it)) }
    }

    override fun onStop() {
        super.onStop()
        // A new app icon is applied once the app is off screen (see AppIconSwitcher).
        if (!isChangingConfigurations) AppIconSwitcher.applyPendingIcon()
    }

    /** `--es demoScreen timer --es demoRoutine <id> --ei demoElapsed <seconds>`; see [DemoLaunch]. */
    private fun demoLaunchFromIntent(): DemoLaunch? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return DemoLaunch.parse(
            intent.getStringExtra("demoScreen"),
            intent.getStringExtra("demoRoutine"),
            intent.getIntExtra("demoElapsed", 0)
        )
    }
}
