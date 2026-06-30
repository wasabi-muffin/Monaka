package dev.gmvalentino.monaka.relay

import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.runtime.StoreRegistry
import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

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
    /**
     * Internal callback invoked on every [dispatch] call to record the target class in the
     * enclosing [DefaultRelay.targets] set. `null` for custom [Relay] implementations that
     * construct [RelayScope] directly.
     */
    internal val trackTarget: ((KClass<out Store<*, *, *>>) -> Unit)? = null,
) {
    /**
     * Relay [action] to registered instances of the store class [target].
     *
     * The action type [A] is inferred from [target], so the compiler verifies [action] is an
     * action the target store actually accepts.
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
        trackTarget?.invoke(target as KClass<out Store<*, *, *>>)
        @Suppress("UNCHECKED_CAST")
        val targets = registry.getAll(kClass = target as KClass<out Store<StateMarker, A, EffectMarker>>)
        targets.forEach { store ->
            if (id == null || store.id == id) store.dispatch(action = action)
        }
    }
}
