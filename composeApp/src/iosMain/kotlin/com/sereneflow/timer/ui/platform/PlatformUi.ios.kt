package com.sereneflow.timer.ui.platform

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@Composable
actual fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String
) {
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(title, message, confirmLabel, destructive, dismissLabel) {
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        alert.addAction(UIAlertAction.actionWithTitle(dismissLabel, UIAlertActionStyleCancel) { currentOnDismiss() })
        alert.addAction(
            UIAlertAction.actionWithTitle(
                confirmLabel,
                if (destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault
            ) { currentOnConfirm() }
        )
        present(alert)
        onDispose { alert.dismissIfPresented() }
    }
}

@Composable
actual fun ActionMenu(
    expanded: Boolean,
    title: String?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
) {
    if (!expanded) return
    val currentActions by rememberUpdatedState(actions)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(Unit) {
        val sheet = UIAlertController.alertControllerWithTitle(title, null, UIAlertControllerStyleActionSheet)
        currentActions.forEach { action ->
            sheet.addAction(
                UIAlertAction.actionWithTitle(
                    action.title,
                    if (action.destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault
                ) {
                    currentOnDismiss()
                    action.onClick()
                }
            )
        }
        sheet.addAction(UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel) { currentOnDismiss() })
        present(sheet)
        onDispose { sheet.dismissIfPresented() }
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

// The status bar already follows the content's brightness under Compose on iOS.
@Composable
actual fun SystemBarAppearance(darkBackground: Boolean) {}

actual object PlatformIcons {
    actual val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBackIos
    actual val More: ImageVector = Icons.Rounded.MoreHoriz
}

@OptIn(ExperimentalForeignApi::class)
private fun present(controller: UIAlertController) {
    val presenter = topViewController() ?: return
    // iPad presents action sheets as popovers, which need an anchor or UIKit throws.
    controller.popoverPresentationController?.let { popover ->
        val view = presenter.view
        popover.sourceView = view
        view.bounds.useContents {
            popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
        }
        popover.permittedArrowDirections = 0u
    }
    presenter.presentViewController(controller, animated = true, completion = null)
}

private fun UIAlertController.dismissIfPresented() {
    if (presentingViewController != null && !isBeingDismissed()) {
        dismissViewControllerAnimated(true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}
