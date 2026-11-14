import ComposeApp
import StoreKit
import UIKit

/// Swift side of the Kotlin `StoreReviewBridge`: asks for the App Store's rating prompt in the
/// window the user is looking at. iOS decides whether it shows.
final class StoreReviewManager: NSObject, StoreReviewBridge {
    func requestReview() {
        DispatchQueue.main.async {
            let scene = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .first { $0.activationState == .foregroundActive }
            if let scene { AppStore.requestReview(in: scene) }
        }
    }
}
