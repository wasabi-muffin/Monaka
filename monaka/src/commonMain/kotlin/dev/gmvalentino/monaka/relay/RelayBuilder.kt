package dev.gmvalentino.monaka.relay

import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.runtime.StoreRegistry

/**
 * Builder for [Relay]. Configure which source events should be relayed to other stores.
 *
 * Any combination of [state], [effect], and [action] can be called, including multiple
 * times for different subtypes. Unlike a 1:1 binding, **all** matching blocks run for a
 * given event, so a single relay can drive several dispatches per emission.
 */
@MonakaDsl
public class RelayBuilder<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker> {

    @PublishedApi
    internal val stateHandlers: MutableList<(SourceState, StoreRegistry) -> Unit> = mutableListOf()

    @PublishedApi
    internal val effectHandlers: MutableList<(SourceEffect, StoreRegistry) -> Unit> = mutableListOf()

    @PublishedApi
    internal val actionHandlers: MutableList<(SourceAction, StoreRegistry) -> Unit> = mutableListOf()

    /**
     * React to source states of type [S]. The block runs with a [RelayScope] whose
     * [RelayScope.event] is the matched state.
     *
     * ```kotlin
     * state<AuthState.SignedIn> { dispatch(CartStore::class, CartAction.LoadForUser(event.user.id)) }
     * ```
     */
    public inline fun <reified S : SourceState> state(crossinline block: RelayScope<S>.() -> Unit) {
        stateHandlers.add { state, registry -> (state as? S)?.let { RelayScope(event = it, registry = registry).block() } }
    }

    /**
     * React to source effects of type [E]. The block runs with a [RelayScope] whose
     * [RelayScope.event] is the matched effect.
     *
     * ```kotlin
     * effect<CartEffect.CartChanged> { dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total)) }
     * ```
     */
    public inline fun <reified E : SourceEffect> effect(crossinline block: RelayScope<E>.() -> Unit) {
        effectHandlers.add { effect, registry -> (effect as? E)?.let { RelayScope(event = it, registry = registry).block() } }
    }

    /**
     * React to source actions of type [A]. The block runs with a [RelayScope] whose
     * [RelayScope.event] is the matched action.
     *
     * ```kotlin
     * action<AuthAction.SignOut> { dispatch(SessionStore::class, SessionAction.Invalidate) }
     * ```
     */
    public inline fun <reified A : SourceAction> action(crossinline block: RelayScope<A>.() -> Unit) {
        actionHandlers.add { action, registry -> (action as? A)?.let { RelayScope(event = it, registry = registry).block() } }
    }

    @PublishedApi
    internal fun build(
        from: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    ): Relay<SourceState, SourceAction, SourceEffect> = DefaultRelay(
        source = from,
        stateHandler = stateHandlers.mergeOrNull(),
        effectHandler = effectHandlers.mergeOrNull(),
        actionHandler = actionHandlers.mergeOrNull(),
    )

    /**
     * Collapses a list of `(T, StoreRegistry) -> Unit` handlers into a single function that
     * runs all of them in order, or returns `null` when the list is empty (so no collector
     * coroutine is launched for that channel).
     */
    private fun <Source> List<(Source, StoreRegistry) -> Unit>.mergeOrNull(): ((Source, StoreRegistry) -> Unit)? =
        if (isEmpty()) null else { event, registry -> forEach { handler -> handler(event, registry) } }
}
