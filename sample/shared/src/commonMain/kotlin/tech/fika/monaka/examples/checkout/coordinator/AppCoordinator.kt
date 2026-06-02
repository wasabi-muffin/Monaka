package tech.fika.monaka.examples.checkout.coordinator

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.binder.Binder
import tech.fika.monaka.examples.checkout.data.AuthRepository
import tech.fika.monaka.examples.checkout.auth.AuthStateMachine
import tech.fika.monaka.examples.checkout.auth.AuthStore
import tech.fika.monaka.examples.checkout.data.CartRepository
import tech.fika.monaka.examples.checkout.cart.CartStateMachine
import tech.fika.monaka.examples.checkout.cart.CartStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutStateMachine
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore
import tech.fika.monaka.examples.checkout.data.PaymentRepository
import tech.fika.monaka.runtime.StoreRegistry
import tech.fika.monaka.runtime.register

// ─────────────────────────────────────────────────────────────────────────────
// This example models an e-commerce checkout flow split across three machines:
//
//   AuthStateMachine       — sign-in / sign-out
//   CartStateMachine       — shopping cart contents
//   CheckoutStateMachine   — payment flow
//
// The machines are unaware of each other. AppCoordinator wires them using
// [Binder]s so that:
//
//   AuthStateMachine (state: SignedIn)    ──▶ CartStateMachine  (LoadForUser)
//   AuthStateMachine (state: SignedOut)   ──▶ CartStateMachine  (Clear)
//   AuthStateMachine (state: SignedOut)   ──▶ CheckoutStateMachine (Cancel)
//   CartStateMachine (effect: CartChanged)──▶ CheckoutStateMachine (SyncCart)
//   CheckoutStateMachine (state: Done)    ──▶ CartStateMachine  (Clear)
//
// Each bridge is a single, readable object declaration. No machine holds a
// reference to another — all coupling lives here.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates and connects the three machines using a [StoreRegistry] and [Binder]s.
 *
 * Bridge topology:
 *
 * ```
 *                        state(SignedIn)  ──────▶  CartStateMachine.LoadForUser
 *  AuthStateMachine ──────┤
 *                        state(SignedOut) ──────▶  CartStateMachine.Clear
 *                    │
 *                        state(SignedOut) ──────▶  CheckoutStateMachine.Cancel
 *
 *  CartStateMachine  ─── effect(CartChanged)  ──────▶  CheckoutStateMachine.SyncCart
 *
 *  CheckoutStateMachine ─ state(Done)         ──────▶  CartStateMachine.Clear
 * ```
 *
 * ### Lifecycle
 * All bridge coroutines run in [scope]. Pass `viewModelScope` so they are
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
        // ── 1. Declare the bridge topology via Binders ────────────────────────

        registry.install(
            AuthToCartBinder,
            AuthToCheckoutBinder,
            CartToCheckoutBinder,
            CheckoutToCartBinder,
        )

        // ── 2. Register machines — binders are applied automatically ──────────

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
