package dev.gmvalentino.monaka.relay

import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.runtime.StoreRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

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
 *     state<AuthState.SignedIn>  { dispatch(CartStore::class, CartAction.LoadForUser(event.user.id)) }
 *     state<AuthState.SignedOut> {
 *         dispatch(CartStore::class, CartAction.Clear)
 *         dispatch(CheckoutStore::class, CheckoutAction.Cancel)
 *     }
 * }
 *
 * registry.bind(authRelay)
 * ```
 *
 * Implement this interface directly when the [relay] DSL is insufficient, such as when
 * relaying logic requires injected dependencies or more complex coordination.
 */
public interface Relay<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker> {
    /** The [KClass] of the [Store] subclass this relay observes. */
    public val source: KClass<out Store<SourceState, SourceAction, SourceEffect>>

    /**
     * The [Store] classes this relay may dispatch actions to.
     *
     * When non-empty, the relay handler is **skipped** on any emission where none of these
     * classes has a registered instance in the [StoreRegistry] — preventing spurious handler
     * work (and any side effects inside the handler) when all targets are temporarily absent.
     * The collector coroutine itself keeps running; the handler resumes firing on the next
     * emission once any declared target class is registered again.
     *
     * For relays built with the [relay] DSL this set is populated automatically: every
     * [RelayScope.dispatch] call records its target class here on first emission, so no manual
     * declaration is needed. Custom [Relay] implementations that do not override this property
     * retain the previous behavior — the handler always fires regardless of target availability.
     */
    public val targets: Set<KClass<out Store<*, *, *>>> get() = emptySet()

    /**
     * Wire [source] to [registry] by launching collector coroutines in [scope].
     *
     * Returns the launched [Job]s so the registry can cancel them when the source
     * store is unregistered.
     */
    public fun apply(source: Store<*, *, *>, registry: StoreRegistry, scope: CoroutineScope): List<Job>
}
