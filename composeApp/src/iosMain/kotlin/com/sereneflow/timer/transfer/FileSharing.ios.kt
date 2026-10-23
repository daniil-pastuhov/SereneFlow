package com.sereneflow.timer.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sereneflow.timer.ui.platform.present
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun shareFile(fileName: String, mimeType: String, content: String) {
    // A fresh folder each time, so an old share never lingers under the same name.
    val dir = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("shared", isDirectory = true) ?: return
    val files = NSFileManager.defaultManager
    files.removeItemAtURL(dir, error = null)
    files.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val url = dir.URLByAppendingPathComponent(fileName) ?: return
    val written = NSString.create(string = content)
        .writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    if (!written) return
    present(UIActivityViewController(activityItems = listOf(url), applicationActivities = null))
}

@Composable
actual fun rememberRoutineFilePicker(onPicked: (String) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    return remember {
        // UIKit holds its delegate weakly: keep it for as long as the picker can be shown.
        val delegate = PickerDelegate { currentOnPicked.value(it) }
        val open = {
            val types = listOfNotNull(
                UTType.typeWithIdentifier(ROUTINE_TYPE_IDENTIFIER),
                UTType.typeWithFilenameExtension(RoutineTransfer.FILE_EXTENSION),
                UTTypeJSON,
                // Files from other apps may arrive without a type the system recognises.
                UTTypeData
            )
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            present(picker)
        }
        open
    }
}

private class PickerDelegate(private val onPicked: (String) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        readRoutineFile(url)?.let(onPicked)
    }
}

/**
 * The text of the file at [url], reached under its security scope where it has one (a file
 * opened from Files or another app). Null when it can't be read.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun readRoutineFile(url: NSURL): String? {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        return NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/** The exported type of `.sereneflow` files, declared in iosApp/Info.plist. */
const val ROUTINE_TYPE_IDENTIFIER = "com.sereneflow.timer.routine"
