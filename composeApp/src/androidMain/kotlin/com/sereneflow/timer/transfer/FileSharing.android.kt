package com.sereneflow.timer.transfer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.FileProvider
import com.sereneflow.timer.platform.AndroidAppContext
import java.io.File

actual fun shareFile(fileName: String, mimeType: String, content: String) {
    val context = AndroidAppContext.appContext ?: return
    // A fresh folder each time, so an old share never lingers under the same name.
    val dir = File(context.cacheDir, SHARE_DIR).apply {
        deleteRecursively()
        mkdirs()
    }
    val file = File(dir, fileName).apply { writeText(content) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, fileName)
        clipData = ClipData.newRawUri(fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )
}

@Composable
actual fun rememberRoutineFilePicker(onPicked: (String) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val context = AndroidAppContext.appContext ?: return@rememberLauncherForActivityResult
        uri?.let { readText(context, it) }?.let { currentOnPicked.value(it) }
    }
    // A .sereneflow file has no registered type, so the picker can't filter by it; JSON and
    // "anything" (application/octet-stream is what most providers report for it) are both offered.
    return { launcher.launch(arrayOf(RoutineTransfer.MIME_TYPE, "application/octet-stream", "text/plain")) }
}

/** The text of [uri], or null when it can't be read or is unreasonably large for a routine file. */
fun readText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readNBytesCompat(MAX_FILE_BYTES + 1)
        if (bytes.size > MAX_FILE_BYTES) null else bytes.decodeToString()
    }
} catch (_: Exception) {
    null
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val SHARE_DIR = "shared"
private const val MAX_FILE_BYTES = 5 * 1024 * 1024
