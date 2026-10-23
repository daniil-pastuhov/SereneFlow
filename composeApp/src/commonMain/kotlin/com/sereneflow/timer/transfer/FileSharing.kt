package com.sereneflow.timer.transfer

import androidx.compose.runtime.Composable

/**
 * Offers [content] as a file named [fileName] through the system share sheet (save to Files,
 * AirDrop, mail, ...). Android: a FileProvider URI in an ACTION_SEND chooser; iOS: a
 * UIActivityViewController.
 */
expect fun shareFile(fileName: String, mimeType: String, content: String)

/**
 * Returns an action that lets the user pick a `.sereneflow` (or JSON) file and calls [onPicked]
 * with its text. Android: the Storage Access Framework; iOS: a UIDocumentPickerViewController.
 * Nothing is called when the user cancels or the file can't be read.
 */
@Composable
expect fun rememberRoutineFilePicker(onPicked: (String) -> Unit): () -> Unit
