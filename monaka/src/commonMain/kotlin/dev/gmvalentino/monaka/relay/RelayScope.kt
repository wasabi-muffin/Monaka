package dev.gmvalentino.monaka.relay

import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.runtime.StoreRegistry

/**
 * Implicit receiver inside every `state`/`effect`/`action` relay block.
 *
 * Exposes the matched [event] (typed to the bound subtype) and [dispatch] for relaying
 * actions into target stores resolved from the enclosing [StoreRegistry].
 */
public class RelayScope<out Event> @PublishedApi internal constructor(
    /** The matched source event (state, effect, or action) that triggered this relay block. */
    public val event: Event,
    @PublishedApi internal val registry: StoreRegistry,
) {
    /**
     * Relay [action] to registered instances of store [S], identified by reified type argument.
     *
     * When [id] is `null` (default), the action is dispatched to **every** registered
     * instance of [S]. When [id] is provided, it is dispatched only to the instance of
     * [S] whose [Store.id] matches — a no-op if no such instance is registered.
     *
     * ```kotlin
     * dispatch<CartStore>(CartAction.Clear)                 // all CartStore instances
     * dispatch<CartStore>(CartAction.Clear, id = cartId)    // the one with this id
     * ```
     *
     * Note: in this form [action] is typed as the [ActionMarker] base rather than [S]'s
     * specific action type — Kotlin cannot infer the action type while [S] is given explicitly.
     * An action the target store has no handler for is treated as unhandled (see
     * [dev.gmvalentino.monaka.plugin.Plugin.onRejected]); it does not throw. Use the [KClass] overload
     * below when you want the compiler to verify the action belongs to the target store.
     */
    public inline fun <reified S : Store<*, *, *>> dispatch(action: ActionMarker, id: String? = null) {
        @Suppress("UNCHECKED_CAST")
        val targets = registry.getAll(kClass = S::class as KClass<out Store<StateMarker, ActionMarker, EffectMarker>>)
        targets.forEach { store ->
            if (id == null || store.id == id) store.dispatch(action = action)
        }
    }

    /**
     * Relay [action] to registered instances of the store class [target].
     *
     * Equivalent to the reified overload, but the action type [A] is inferred from [target],
     * so the compiler verifies [action] is an action the target store actually accepts.
     *
     * When [id] is `null` (default), the action is dispatched to **every** registered instance
     * of [target]. When [id] is provided, only the instance whose [Store.id] matches receives it.
     *
     * ```kotlin
     * dispatch(CartStore::class, CartAction.Clear)              // all CartStore instances
     * dispatch(CartStore::class, CartAction.Clear, id = cartId) // the one with this id
     * ```
     */
    public fun <A : ActionMarker> dispatch(target: KClass<out Store<*, A, *>>, action: A, id: String? = null) {
        @Suppress("UNCHECKED_CAST")
        val targets = registry.getAll(kClass = target as KClass<out Store<StateMarker, A, EffectMarker>>)
        targets.forEach { store ->
            if (id == null || store.id == id) store.dispatch(action = action)
        }
    }
}
