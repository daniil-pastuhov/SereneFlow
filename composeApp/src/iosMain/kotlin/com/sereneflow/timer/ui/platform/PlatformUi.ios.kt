package com.sereneflow.timer.ui.platform

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UINavigationController
import platform.UIKit.UIPickerView
import platform.UIKit.UIPickerViewDataSourceProtocol
import platform.UIKit.UIPickerViewDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.navigationItem
import platform.UIKit.popoverPresentationController
import platform.UIKit.presentationController
import platform.darwin.NSInteger
import platform.darwin.NSObject

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
actual fun DurationPicker(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(Unit) {
        val picker = UIPickerView()
        // The picker holds its data source and delegate weakly; this effect keeps them alive.
        val wheels = DurationWheels(onSwipeDismiss = { currentOnDismiss() })
        picker.dataSource = wheels
        picker.delegate = wheels
        picker.selectRow((initialSeconds / 60).coerceAtMost(MAX_PICKER_MINUTES).toLong(), inComponent = 0, animated = false)
        picker.selectRow((initialSeconds % 60).toLong(), inComponent = 1, animated = false)

        val content = UIViewController()
        content.title = "Duration"
        content.view.backgroundColor = UIColor.systemBackgroundColor
        picker.translatesAutoresizingMaskIntoConstraints = false
        content.view.addSubview(picker)
        NSLayoutConstraint.activateConstraints(
            listOf(
                picker.leadingAnchor.constraintEqualToAnchor(content.view.leadingAnchor),
                picker.trailingAnchor.constraintEqualToAnchor(content.view.trailingAnchor),
                picker.centerYAnchor.constraintEqualToAnchor(content.view.safeAreaLayoutGuide.centerYAnchor)
            )
        )
        content.navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
            primaryAction = UIAction.actionWithHandler { currentOnDismiss() }
        )
        content.navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemDone,
            primaryAction = UIAction.actionWithHandler {
                currentOnConfirm(
                    pickedDuration(
                        minutes = picker.selectedRowInComponent(0).toInt(),
                        seconds = picker.selectedRowInComponent(1).toInt()
                    )
                )
            }
        )

        val sheet = UINavigationController(rootViewController = content)
        sheet.sheetPresentationController?.let {
            it.detents = listOf(UISheetPresentationControllerDetent.mediumDetent())
            it.prefersGrabberVisible = true
        }
        sheet.presentationController?.delegate = wheels
        present(sheet)
        onDispose { sheet.dismissIfPresented() }
    }
}

/** Minutes and seconds wheels of [DurationPicker], and the sheet's swipe-to-dismiss. */
private class DurationWheels(
    private val onSwipeDismiss: () -> Unit
) : NSObject(), UIPickerViewDataSourceProtocol, UIPickerViewDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol {

    override fun numberOfComponentsInPickerView(pickerView: UIPickerView): NSInteger = 2

    override fun pickerView(pickerView: UIPickerView, numberOfRowsInComponent: NSInteger): NSInteger =
        if (numberOfRowsInComponent == 0L) MAX_PICKER_MINUTES + 1L else 60L

    @ObjCSignatureOverride
    override fun pickerView(pickerView: UIPickerView, titleForRow: NSInteger, forComponent: NSInteger): String =
        if (forComponent == 0L) "$titleForRow min" else "$titleForRow sec"

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        onSwipeDismiss()
    }
}

@Composable
actual fun rememberReduceMotion(): Boolean {
    var reduce by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification, null, NSOperationQueue.mainQueue
        ) { _ -> reduce = UIAccessibilityIsReduceMotionEnabled() }
        onDispose { center.removeObserver(observer) }
    }
    return reduce
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

/** Presents [controller] over whatever is on screen, anchored mid-screen where it is a popover (iPad). */
@OptIn(ExperimentalForeignApi::class)
internal fun present(controller: UIViewController) {
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

private fun UIViewController.dismissIfPresented() {
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
    // Not onto one on its way out (an action sheet whose action presents something next).
    while (top?.presentedViewController?.isBeingDismissed() == false) {
        top = top.presentedViewController
    }
    return top
}
