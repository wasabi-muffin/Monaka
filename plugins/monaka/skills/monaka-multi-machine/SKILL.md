---
name: monaka-multi-machine
description: >-
  Coordinate multiple Monaka stores with StoreRegistry and relay: fan out a state/effect/action
  from one store into actions on other stores without direct coupling. Use when connecting several
  state machines (e.g. Auth → Cart → Checkout), declaring relays, wiring a StoreRegistry, or adding
  registry-wide plugins. For single machines see monaka-state-machines.
---

# Multi-machine coordination

Keep each machine unaware of the others. A **`Relay`** declares "when store X emits Y, dispatch Z to
store W"; a **`StoreRegistry`** holds the store instances and applies relays as stores register.
All coupling lives in the relay/coordinator layer. Full docs:
https://monaka.gmvalentino.dev/guide/multi-machine/

## Step 1 — give each machine a distinct `Store` type

Relays and the registry key stores **by class**, so each machine needs its own `Store` subtype.
Wrap the config with a `Store`-by-delegation class:

```kotlin
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store

class AuthStore(
    stateMachine: AuthStateMachine,           // : StateMachine<…> by stateMachine(builder = { … })
    scope: CoroutineScope,
    initialState: AuthState? = null,
) : Store<AuthState, AuthAction, AuthEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
```

Do the same for `CartStore`, `CheckoutStore`, etc. Now `AuthStore::class` is a usable key.

## Step 2 — declare relays

`relay(from = SourceStore::class) { … }` observes every registered instance of the source. Inside,
match on `state<S>`, `effect<E>`, or `action<A>`; the matched item is `event`, and
`dispatch(TargetStore::class, action)` sends into the target. A relay can fan out to several
targets, and you can mix state/effect/action blocks. Declaring each relay as an `object` keeps it
tidy:

```kotlin
import dev.gmvalentino.monaka.relay.Relay
import dev.gmvalentino.monaka.relay.relay

// Auth state → Cart + Checkout actions
object AuthRelay : Relay<AuthState, AuthAction, AuthEffect> by relay(from = AuthStore::class, builder = {
    state<AuthState.SignedIn> {
        dispatch(CartStore::class, CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch(CartStore::class, CartAction.Clear)
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }
})

// Cart effect → Checkout action
object CartRelay : Relay<CartState, CartAction, CartEffect> by relay(from = CartStore::class, builder = {
    effect<CartEffect.CartChanged> {
        dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total))
    }
})
```

- `state<S>` fires on transitions into `S`; `effect<E>` on emitted effects; `action<A>` on
  dispatched actions.
- `dispatch(Target::class, action)` reaches **all** registered instances of `Target`. Pass
  `id = "…"` to target a single instance by its `Store.id`.
- The action type is inferred from the target class, so the compiler checks it's an action that
  store accepts.

## Step 3 — wire a registry

`StoreRegistry(bridgeScope)` runs all relay coroutines in `bridgeScope`. `bind` the relays, then
`register` the stores — order doesn't matter; a relay starts observing as soon as a matching source
registers.

```kotlin
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.gmvalentino.monaka.runtime.register

class AppCoordinator(
    authRepo: AuthRepository,
    cartRepo: CartRepository,
    paymentRepo: PaymentRepository,
    scope: CoroutineScope, // pass viewModelScope
) {
    val registry = StoreRegistry(bridgeScope = scope)

    init {
        registry.bind(AuthRelay, CartRelay, CheckoutRelay)

        AuthStore(AuthStateMachine(authRepo), scope).register(registry)
        CartStore(CartStateMachine(cartRepo), scope).register(registry)
        CheckoutStore(CheckoutStateMachine(paymentRepo), scope).register(registry)
    }
}
```

`Store.register(registry)` returns the store (fluent). Retrieve instances later with
`registry.get(CartStore::class)`, `registry.getAll(...)`, `registry.getById(id)`, or check with
`CartStore::class in registry`.

## Registry-wide plugins

Install a plugin **factory** on the registry to attach one instance per store (current and future).
The factory receives a `PluginScope` exposing `store` and a computed `name`:

```kotlin
val registry = StoreRegistry(bridgeScope = scope) {
    install { LoggingPlugin(tag = name) }                       // tag each store by its name
    install { plugin { onTransition { println("[$name] $fromState → $toState") } } }
}
```

## Lifetime & threading

- **Auto cleanup:** a registered store is stopped and unregistered automatically when its owning
  scope is canceled (e.g. `viewModelScope` cleared). This relies on scope cancellation — calling
  `store.stop()` directly does **not** trigger it, so `registry.unregister(store)` manually after an
  explicit `stop()` (e.g. in a Compose `DisposableEffect`).
- **Move without stopping:** `registry.unregister(store)` alone removes it but leaves it running.
- **Not thread-safe:** call `register`/`unregister`/`bind`/`install`/`get` from one thread, and pass
  a main-confined `bridgeScope` (e.g. `viewModelScope`, which runs on `Dispatchers.Main`). Reuse the
  same scope that owns the stores so all relay work cancels together.
- **Target absent:** if a relay's target class has no registered instance when an event fires, that
  dispatch is skipped (the relay keeps running) and resumes when an instance registers again.
