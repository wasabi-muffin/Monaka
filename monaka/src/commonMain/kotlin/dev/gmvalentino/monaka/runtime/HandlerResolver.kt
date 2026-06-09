package dev.gmvalentino.monaka.runtime

import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.handler.ActionHandler
import dev.gmvalentino.monaka.handler.LifecycleHandler
import dev.gmvalentino.monaka.handler.StateChangeHandler
import dev.gmvalentino.monaka.handler.StateErrorHandler
import dev.gmvalentino.monaka.handler.StateUpdateHandler

/**
 * Resolves handler registrations against the current state.
 *
 * Encapsulates two lookup strategies used by [DefaultStore]:
 * - [resolveActionHandler] — two-level lookup (state class → action class) with ancestor fallback.
 * - [resolveHandler] — single-level lookup (state class → handler) with ancestor fallback.
 *
 * Both strategies share [ancestorCache] and [registeredStates], computed once at construction
 * from the union of all handler map keys.
 *
 * Not thread-safe by design — [ancestorCache] is written and read only from the single
 * sequential processing coroutine inside [DefaultStore].
 */
internal class HandlerResolver<State : StateMarker, Action : ActionMarker, Effect : EffectMarker>(
    private val actionHandlers: Map<KClass<out State>, Map<KClass<out Action>, ActionHandler<State, Action, Effect>>>,
    enterHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>,
    exitHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>,
    updateHandlers: Map<KClass<out State>, StateUpdateHandler<State, Action, Effect>>,
    lifecycleHandlers: Map<KClass<out State>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>,
    errorHandlers: Map<KClass<out State>, StateErrorHandler<State, Action, Effect>>,
) {
    private val ancestorCache = HashMap<KClass<out State>, List<KClass<out State>>>()
    private val registeredStates: Set<KClass<out State>> = buildSet {
        addAll(actionHandlers.keys)
        addAll(enterHandlers.keys)
        addAll(exitHandlers.keys)
        addAll(updateHandlers.keys)
        addAll(lifecycleHandlers.keys)
        addAll(errorHandlers.keys)
    }

    /**
     * Returns all registered state classes that are supertypes of [state]'s class,
     * in the order they were registered across all handler maps.
     *
     * Computed once per unique state class and cached, so every subsequent call for the
     * same state class is an O(1) map lookup.
     */
    fun ancestorsFor(state: State): List<KClass<out State>> = ancestorCache.getOrPut(state::class) {
        registeredStates.filter { it != state::class && it.isInstance(state) }
    }

    /**
     * Resolve the handler for the given [state] + [action] pair.
     *
     * Priority:
     * 1. Exact state match — `actionHandlers[state::class][action::class]`
     * 2. Registered ancestor classes in insertion order via [ancestorsFor].
     * 3. `null` → [dev.gmvalentino.monaka.plugin.Plugin.onUnhandled] is called by the caller.
     */
    fun resolveActionHandler(state: State, action: Action): ActionHandler<State, Action, Effect>? {
        actionHandlers[state::class]?.get(key = action::class)?.let { return it }
        for (ancestorClass in ancestorsFor(state)) {
            actionHandlers[ancestorClass]?.get(action::class)?.let { return it }
        }
        return null
    }

    /**
     * Generic exact-match + ancestor lookup for all handler maps except [actionHandlers].
     * Returns the first value whose registered [KClass] key matches [state] exactly,
     * or is a registered ancestor of it via [ancestorsFor].
     */
    fun <H> resolveHandler(handlerMap: Map<KClass<out State>, H>, state: State): H? {
        handlerMap[state::class]?.let { return it }
        for (ancestorClass in ancestorsFor(state)) {
            handlerMap[ancestorClass]?.let { return it }
        }
        return null
    }
}
