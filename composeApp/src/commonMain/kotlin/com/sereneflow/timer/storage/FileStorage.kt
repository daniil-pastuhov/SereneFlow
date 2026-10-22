package com.sereneflow.timer.storage

import kotlinx.serialization.json.Json

/** Minimal text-file storage in the app's private data directory. */
interface FileStorage {
    fun read(name: String): String?
    fun write(name: String, content: String)
}

expect fun createFileStorage(): FileStorage

internal val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
