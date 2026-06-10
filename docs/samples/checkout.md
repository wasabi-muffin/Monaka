# Checkout (multi-machine)

**Source:** `sample/shared/…/examples/checkout/`

The checkout example models an e-commerce flow split across three independent state machines:
Auth, Cart, and Checkout. None of the machines holds a reference to the others — all coupling
lives in a single `AppCoordinator` that wires them together using `StoreRegistry` and `Relay`s.

---

## Machines

| Machine | States | Responsibility |
|---|---|---|
| `AuthStateMachine` | `SignedOut`, `SigningIn`, `SignedIn` | Sign-in / sign-out |
| `CartStateMachine` | `Empty`, `Loading`, `Loaded`, `Error` | Shopping cart contents |
| `CheckoutStateMachine` | `Idle`, `Summary`, `Processing`, `Done`, `Failed` | Payment flow |

Each machine is unaware that the others exist. They communicate only through actions that
arrive via relays — from the machine's perspective, every action was dispatched by a local
caller.

---

## Relay topology

```
AuthStore  state(SignedIn)      ──▶ CartStore.LoadForUser(event.user.id)
AuthStore  state(SignedOut)     ──▶ CartStore.Clear
AuthStore  state(SignedOut)     ──▶ CheckoutStore.Cancel

CartStore  effect(CartChanged)  ──▶ CheckoutStore.SyncCart(event.items, event.total)

CheckoutStore  state(Done)      ──▶ CartStore.Clear
```

---

## Relay objects

Each relay is declared as an `object` for readability. The `Relay` interface is implemented via
delegation to the `relay { }` DSL:

```kotlin
object AuthRelay : Relay<AuthState, AuthAction, AuthEffect>
    by relay(from = AuthStore::class, builder = {
        state<AuthState.SignedIn> {
            dispatch(CartStore::class, CartAction.LoadForUser(event.user.id))
        }
        state<AuthState.SignedOut> {
            dispatch(CartStore::class, CartAction.Clear)
            dispatch(CheckoutStore::class, CheckoutAction.Cancel)
        }
    })

object CartRelay : Relay<CartState, CartAction, CartEffect>
    by relay(from = CartStore::class, builder = {
        effect<CartEffect.CartChanged> {
            dispatch(
                CheckoutStore::class,
                CheckoutAction.SyncCart(items = event.items, total = event.total)
            )
        }
    })

object CheckoutRelay : Relay<CheckoutState, CheckoutAction, CheckoutEffect>
    by relay(from = CheckoutStore::class, builder = {
        state<CheckoutState.Done> {
            dispatch(CartStore::class, CartAction.Clear)
        }
    })
```

The `event` property on `RelayScope` holds the typed source event (state, effect, or action)
that triggered the block. `dispatch(TargetStore::class, action)` resolves all registered instances of
`TargetStore` from the registry and dispatches to each. The action type is verified by the
compiler against the target store's action type.

---

## AppCoordinator

`AppCoordinator` owns the registry, installs the relays, and registers the machines. It is the
only class that knows all three machines exist:

```kotlin
class AppCoordinator(
    authRepository: AuthRepository,
    cartRepository: CartRepository,
    paymentRepository: PaymentRepository,
    scope: CoroutineScope,
) {
    val registry = StoreRegistry(bridgeScope = scope)

    init {
        // 1. Declare the wiring — relays can be bound before stores are registered
        registry.bind(AuthRelay, CartRelay, CheckoutRelay)

        // 2. Register stores — matching relays start observing immediately
        AuthStore(AuthStateMachine(authRepository), scope).register(registry)
        CartStore(CartStateMachine(cartRepository), scope).register(registry)
        CheckoutStore(CheckoutStateMachine(paymentRepository), scope).register(registry)
    }
}
```

Relays and stores can be installed in any order — if a store is registered before its relay,
the relay starts observing it retroactively. If a relay is installed before its source store,
it starts observing as soon as the store is registered.

---

## ViewModel integration

```kotlin
class AppViewModel(
    authRepo: AuthRepository,
    cartRepo: CartRepository,
    paymentRepo: PaymentRepository,
) : ViewModel() {
    private val coordinator = AppCoordinator(
        authRepository = authRepo,
        cartRepository = cartRepo,
        paymentRepository = paymentRepo,
        scope = viewModelScope,   // relay coroutines live here; cancelled on clear
    )

    val authStore     get() = coordinator.registry.get(AuthStore::class)
    val cartStore     get() = coordinator.registry.get(CartStore::class)
    val checkoutStore get() = coordinator.registry.get(CheckoutStore::class)
}
```

Each screen collects from its own store. The relay topology is invisible to the UI — a sign-out
on the Auth screen causes the Cart store to clear automatically, with no code in the Cart screen
or the Cart machine knowing why.

---

## Patterns demonstrated

### Separation of concerns via relay

Each machine handles only its own domain. The relay `object` declarations form a single,
readable topology document. Adding a new connection (e.g. "when CheckoutStore emits
`PaymentFailed`, dispatch `CartAction.Unlock`") means adding one block to one relay object —
no changes to the machines themselves.

### State relay vs. effect relay

`AuthRelay` reacts to **states** (`SignedIn`, `SignedOut`) — the relay fires on every state
emission that matches the type, including when the machine first enters that state.

`CartRelay` reacts to an **effect** (`CartChanged`) — the relay fires only when that one-shot
effect is emitted, not on every state change. This is the right trigger when the downstream
action should happen in response to a specific event rather than continuously while in a state.

### `register` and automatic cleanup

`register` attaches an `invokeOnCompletion` callback that stops and unregisters the store when
its owning `CoroutineScope` is cancelled. When each store owns its own `viewModelScope`, calling
`register` is all that is needed — no manual cleanup required:

```kotlin
AuthStore(authMachine, viewModelScope).register(registry)
```

See [Multi-machine coordination](../guide/multi-machine.md) for the full API reference.
