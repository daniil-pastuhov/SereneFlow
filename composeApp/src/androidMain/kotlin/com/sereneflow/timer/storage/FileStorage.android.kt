package com.sereneflow.timer.storage

import com.sereneflow.timer.platform.AndroidAppContext
import java.io.File

private class AndroidFileStorage(private val dir: File) : FileStorage {
    override fun read(name: String): String? {
        val file = File(dir, name)
        return try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    override fun write(name: String, content: String) {
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(File(dir, name))) {
                File(dir, name).writeText(content)
                tmp.delete()
            }
        } catch (_: Exception) {}
    }
}

actual fun createFileStorage(): FileStorage {
    val context = checkNotNull(AndroidAppContext.appContext) {
        "AndroidAppContext.appContext must be set before creating storage"
    }
    return AndroidFileStorage(context.filesDir)
}
