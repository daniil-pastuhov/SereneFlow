package com.sereneflow.timer.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
private class IosFileStorage(private val dirPath: String) : FileStorage {
    override fun read(name: String): String? =
        NSString.stringWithContentsOfFile("$dirPath/$name", NSUTF8StringEncoding, null)

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun write(name: String, content: String) {
        (content as NSString).writeToFile("$dirPath/$name", atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun createFileStorage(): FileStorage {
    val fileManager = NSFileManager.defaultManager
    val dirUrl: NSURL? = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val dirPath = checkNotNull(dirUrl?.path) { "Application Support directory unavailable" }
    return IosFileStorage(dirPath)
}
