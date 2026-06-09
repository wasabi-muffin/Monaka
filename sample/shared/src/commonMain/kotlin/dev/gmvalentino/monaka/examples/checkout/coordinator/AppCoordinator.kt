package dev.gmvalentino.monaka.examples.checkout.coordinator

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.examples.checkout.data.AuthRepository
import dev.gmvalentino.monaka.examples.checkout.auth.AuthStateMachine
import dev.gmvalentino.monaka.examples.checkout.auth.AuthStore
import dev.gmvalentino.monaka.examples.checkout.data.CartRepository
import dev.gmvalentino.monaka.examples.checkout.cart.CartStateMachine
import dev.gmvalentino.monaka.examples.checkout.cart.CartStore
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutStateMachine
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutStore
import dev.gmvalentino.monaka.examples.checkout.data.PaymentRepository
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.gmvalentino.monaka.runtime.register

// ─────────────────────────────────────────────────────────────────────────────
// This example models an e-commerce checkout flow split across three machines:
//
//   AuthStateMachine       — sign-in / sign-out
//   CartStateMachine       — shopping cart contents
//   CheckoutStateMachine   — payment flow
//
// The machines are unaware of each other. AppCoordinator wires them using
// [Relay]s, grouped by source store, so that:
//
//   AuthStore     (state: SignedIn)    ──▶ CartStore      (LoadForUser)
//   AuthStore     (state: SignedOut)   ──▶ CartStore      (Clear)
//   AuthStore     (state: SignedOut)   ──▶ CheckoutStore  (Cancel)
//   CartStore     (effect: CartChanged)──▶ CheckoutStore  (SyncCart)
//   CheckoutStore (state: Done)        ──▶ CartStore      (Clear)
//
// Each relay is a single, readable object declaration keyed by its source store.
// No machine holds a reference to another — all coupling lives here.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates and connects the three machines using a [StoreRegistry] and [Relay]s.
 *
 * Relay topology:
 *
 * ```
 *                  state(SignedIn)  ──────▶  CartStore.LoadForUser
 *  AuthStore ──────┤
 *                  state(SignedOut) ──────▶  CartStore.Clear
 *                  state(SignedOut) ──────▶  CheckoutStore.Cancel
 *
 *  CartStore  ──── effect(CartChanged) ────▶  CheckoutStore.SyncCart
 *
 *  CheckoutStore ─ state(Done)        ──────▶  CartStore.Clear
 * ```
 *
 * ### Lifecycle
 * All relay coroutines run in [scope]. Pass `viewModelScope` so they are
 * cancelled automatically when the ViewModel is cleared.
 *
 * ### Usage in a ViewModel
 * ```kotlin
 * class AppViewModel(
 *     authRepo: AuthRepository,
 *     cartRepo: CartRepository,
 *     paymentRepo: PaymentRepository,
 * ) : ViewModel() {
 *     private val coordinator = AppCoordinator(authRepo, cartRepo, paymentRepo, viewModelScope)
 *     val authMachine     get() = coordinator.registry.get(AuthStateMachine::class)
 *     val cartMachine     get() = coordinator.registry.get(CartStateMachine::class)
 *     val checkoutMachine get() = coordinator.registry.get(CheckoutStateMachine::class)
 *
 *     fun dispose(machine: StateMachine<*, *, *>) = coordinator.registry.unregister(machine)
 * }
 * ```
 */
class AppCoordinator(
    authRepository: AuthRepository,
    cartRepository: CartRepository,
    paymentRepository: PaymentRepository,
    scope: CoroutineScope,
) {
    val registry = StoreRegistry(bridgeScope = scope)

    init {
        // ── 1. Declare the relay topology, grouped by source store ────────────

        registry.install(
            AuthRelay,
            CartRelay,
            CheckoutRelay,
        )

        // ── 2. Register machines — relays start observing automatically ───────

        AuthStore(
            stateMachine = AuthStateMachine(authRepository),
            scope = scope
        ).register(registry)

        CartStore(
            stateMachine = CartStateMachine(cartRepository),
            scope = scope
        ).register(registry)

        CheckoutStore(
            stateMachine = CheckoutStateMachine(paymentRepository),
            scope = scope
        ).register(registry)
    }
}
