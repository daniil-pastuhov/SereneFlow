package com.sereneflow.timer.storage

import kotlinx.serialization.json.Json

/** Minimal text-file storage in the app's private data directory. */
interface FileStorage {
    /** The file's text, or null when it doesn't exist. Throws when it exists but can't be read. */
    fun read(name: String): String?
    fun write(name: String, content: String)
}

expect fun createFileStorage(): FileStorage

internal val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // A value this version doesn't know (an enum entry from a newer version, a null where a
    // default is expected) falls back to the property's default instead of failing the whole file.
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Reads and decodes [name]. A missing file gives [fallback]. A file that can't be read or decoded
 * also gives [fallback] in memory, but is never overwritten by it: the repositories only write on a
 * real change, and an undecodable file is first copied to `<name>.bad` so a later change can't lose it.
 */
internal inline fun <T> FileStorage.load(name: String, fallback: () -> T, decode: (String) -> T): T {
    val json = try {
        read(name)
    } catch (_: Exception) {
        return fallback()
    } ?: return fallback()
    return try {
        decode(json)
    } catch (_: Exception) {
        try {
            write("$name.bad", json)
        } catch (_: Exception) {}
        fallback()
    }
}
