import ComposeApp
import UIKit

/// Swift side of the Kotlin `AppIconBridge`: the alternate-icon API of UIApplication is missing
/// from the Kotlin/Native UIKit bindings, so Settings → App icon calls into this object.
final class AppIconManager: NSObject, AppIconBridge {
    var supportsAlternateIcons: Bool { UIApplication.shared.supportsAlternateIcons }

    var alternateIconName: String? { UIApplication.shared.alternateIconName }

    func setAlternateIconName(name: String?, onDone: @escaping () -> Void) {
        UIApplication.shared.setAlternateIconName(name) { error in
            if let error { NSLog("SereneFlow: could not change the app icon: \(error)") }
            DispatchQueue.main.async { onDone() }
        }
    }
}
