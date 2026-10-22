package com.sereneflow.timer

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sereneflow.timer.platform.AndroidAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.appContext = applicationContext
        enableEdgeToEdge()
        // Only on a fresh start, so a rotation doesn't restart the demo routine.
        val demoLaunch = if (savedInstanceState == null) demoLaunchFromIntent() else null
        setContent {
            App(demoLaunch = demoLaunch)
        }
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
