import ComposeApp
import StoreKit

/// Swift side of the Kotlin `TipStoreBridge`: the tip jar's coffees are consumable in-app
/// purchases, and StoreKit 2 has no Objective-C API, so the Kotlin tip jar calls into this object.
final class TipStoreManager: NSObject, TipStoreBridge {
    private var products: [String: Product] = [:]
    private var updates: Task<Void, Never>?

    override init() {
        super.init()
        // Purchases that complete outside a purchase() call (Ask to Buy approved, a delayed
        // payment) and ones an earlier run left unfinished.
        updates = Task { [weak self] in
            for await result in Transaction.unfinished { await self?.complete(result) }
            for await result in Transaction.updates { await self?.complete(result) }
        }
    }

    deinit { updates?.cancel() }

    func loadProducts(ids: [String], onDone: @escaping ([TipProductInfo]) -> Void) {
        Task { @MainActor in
            let loaded = (try? await Product.products(for: ids)) ?? []
            for product in loaded { products[product.id] = product }
            onDone(loaded.map { TipProductInfo(id: $0.id, displayPrice: $0.displayPrice) })
        }
    }

    func purchase(id: String, onDone: @escaping (TipResult) -> Void) {
        Task { @MainActor in
            guard let product = products[id] else { return onDone(.failed) }
            do {
                switch try await product.purchase() {
                case .success(let verification):
                    onDone(await complete(verification) ? .success : .failed)
                case .pending:
                    onDone(.pending)
                case .userCancelled:
                    onDone(.cancelled)
                @unknown default:
                    onDone(.failed)
                }
            } catch {
                NSLog("SereneFlow: tip purchase failed: \(error)")
                onDone(.failed)
            }
        }
    }

    /// Finishes a verified purchase (so the coffee can be bought again) and records the donation.
    @MainActor
    @discardableResult
    private func complete(_ result: VerificationResult<Transaction>) async -> Bool {
        guard case .verified(let transaction) = result else { return false }
        await transaction.finish()
        TipJar.shared.purchaseCompleted()
        return true
    }
}
