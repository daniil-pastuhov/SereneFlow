package com.sereneflow.timer.storage

import com.sereneflow.timer.platform.AndroidAppContext
import java.io.File

private class AndroidFileStorage(private val dir: File) : FileStorage {
    override fun read(name: String): String? {
        val file = File(dir, name)
        // An existing file that fails to read throws, so it isn't mistaken for a first launch.
        return if (file.exists()) file.readText() else null
    }

    override fun write(name: String, content: String) {
        try {
            // Write a temp file and rename it over the old one, so a crash mid-write can't leave a
            // truncated file behind. If the rename fails, keep the old file rather than rewrite it in place.
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(File(dir, name))) tmp.delete()
        } catch (_: Exception) {}
    }
}

actual fun createFileStorage(): FileStorage {
    val context = checkNotNull(AndroidAppContext.appContext) {
        "AndroidAppContext.appContext must be set before creating storage"
    }
    return AndroidFileStorage(context.filesDir)
}
