package com.sereneflow.timer.platform

import android.content.Context

/** Application context, set by the launcher activity before `App()` is composed. */
object AndroidAppContext {
    var appContext: Context? = null
}
