package dev.gmvalentino.monaka.relay

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.runtime.StoreRegistry

/**
 * Observes a single source store class and relays its state changes, effects, and/or
 * dispatched actions into other stores held by a [StoreRegistry].
 *
 * Unlike a fixed 1:1 wiring, a relay has only a [source] — its targets are resolved
 * lazily at emission time via the registry, so one relay can fan an event out to any
 * number of target stores (see [RelayScope.dispatch]).
 *
 * Create instances with the [relay] factory:
 * ```kotlin
 * val authRelay = relay(from = AuthStore::class) {
 *     state<AuthState.SignedIn>  { dispatch<CartStore>(CartAction.LoadForUser(event.user.id)) }
 *     state<AuthState.SignedOut> {
 *         dispatch<CartStore>(CartAction.Clear)
 *         dispatch<CheckoutStore>(CheckoutAction.Cancel)
 *     }
 * }
 *
 * registry.install(authRelay)
 * ```
 *
 * Implement this interface directly when the [relay] DSL is insufficient, such as when
 * relaying logic requires injected dependencies or more complex coordination.
 */
public interface Relay<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker> {
    public val source: KClass<out Store<SourceState, SourceAction, SourceEffect>>

    /**
     * Wire [source] to [registry] by launching collector coroutines in [scope].
     *
     * Returns the launched [Job]s so the registry can cancel them when the source
     * store is unregistered.
     */
    public fun apply(source: Store<*, *, *>, registry: StoreRegistry, scope: CoroutineScope): List<Job>
}
