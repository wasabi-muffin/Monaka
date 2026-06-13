@file:OptIn(ExperimentalUuidApi::class)

package dev.gmvalentino.monaka.dsl

import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.State as StateMarker

import dev.gmvalentino.monaka.handler.ActionHandler
import dev.gmvalentino.monaka.handler.LifecycleHandler
import dev.gmvalentino.monaka.handler.StateChangeHandler
import dev.gmvalentino.monaka.handler.StateErrorHandler
import dev.gmvalentino.monaka.handler.StateUpdateHandler
import dev.gmvalentino.monaka.plugin.Plugin

/**
 * Root DSL builder for constructing a [dev.gmvalentino.monaka.dsl.StateMachineStore] specification.
 *
 * Obtain an instance via the [dev.gmvalentino.monaka.dsl.store] top-level factory function.
 *
 * ### Handler lookup order
 * When an action is dispatched, the runtime resolves a handler as follows:
 * 1. Look for a handler registered under the **exact runtime class** of the current state.
 * 2. Check each registered ancestor class in **registration order** (the order
 *    `state<T>` blocks appear in the builder). Register more-specific parent blocks
 *    after leaf blocks so that the leaf-nearest ancestor is found first.
 * 3. If nothing matches, notify plugins via [Plugin.onRejected] and skip.
 *
 * The same lookup order applies to state lifecycle hooks ([StateBuilder.onEnter],
 * [StateBuilder.onExit], [StateBuilder.onUpdate]) and application lifecycle hooks
 * ([StateBuilder.onResume], [StateBuilder.onPause], etc.).
 *
 * ### Hierarchical state handlers
 * Register a `state<ParentState>` block to handle an action from **any substate**
 * without listing every leaf explicitly. Leaf registrations still take priority:
 *
 * ```kotlin
 * state<MyState> {                        // matches any MyState subtype as fallback
 *     on<MyAction.Reset> { transition(MyState.Idle })
 * }
 *
 * state<MyState.Loading> {                // exact match — takes priority for its actions
 *     on<MyAction.Cancel> { … }
 *     on<MyAction.Reset>  { … }          // overrides the parent's Reset for Loading only
 * }
 * ```
 */
@MonakaDsl
public class StateMachineBuilder<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {

    /** Unique identifier for the machine being built. */
    public val id: String = Uuid.random().toString()

    /** Human-readable name for the machine being built. */
    public var name: String? = null
        private set

    /** Set a human-readable [name] for this machine. Used by plugins and logging to identify the store. */
    public fun name(name: String) {
        this.name = name
    }

    internal var initialState: State? = null

    // state-class → (action-class → handler)
    @PublishedApi
    internal val actionHandlers: LinkedHashMap<KClass<out State>, LinkedHashMap<KClass<out Action>, ActionHandler<State, Action, Effect>>> =
        LinkedHashMap()

    // state-class → enter / exit / modify hooks
    @PublishedApi
    internal val enterHandlers: LinkedHashMap<KClass<out State>, StateChangeHandler<State, Action, Effect>> = LinkedHashMap()

    @PublishedApi
    internal val exitHandlers: LinkedHashMap<KClass<out State>, StateChangeHandler<State, Action, Effect>> = LinkedHashMap()

    @PublishedApi
    internal val updateHandlers: LinkedHashMap<KClass<out State>, StateUpdateHandler<State, Action, Effect>> = LinkedHashMap()

    // state-class → (lifecycle-event → hook)
    @PublishedApi
    internal val lifecycleHandlers: LinkedHashMap<KClass<out State>, LinkedHashMap<LifecycleEvent, LifecycleHandler<State, Action, Effect>>> =
        LinkedHashMap()

    // state-class → error recovery hook
    @PublishedApi
    internal val errorHandlers: LinkedHashMap<KClass<out State>, StateErrorHandler<State, Action, Effect>> = LinkedHashMap()

    internal val plugins: MutableList<Plugin> = mutableListOf()

    // ── Required configuration ────────────────────────────────────────────────

    /**
     * Set the initial state of the state machine. **Required.**
     *
     * Must be called exactly once before the builder block returns.
     */
    public fun initialState(state: State) {
        check(initialState == null) { "initialState has already been set." }
        initialState = state
    }

    // ── State handlers ────────────────────────────────────────────────────────

    /**
     * Open a handler block scoped to state [SubState].
     *
     * [SubState] may be a concrete leaf state *or* a sealed parent interface.
     * When [SubState] is a parent, its handlers fire for any substate that does not
     * have a more specific registration for the same action.
     *
     * Multiple `state<SubState>` calls for the same [SubState] are merged; later registrations
     * overwrite earlier ones for the same action class or hook type.
     */
    public inline fun <reified SubState : State> state(
        block: StateBuilder<State, SubState, Action, Effect>.() -> Unit,
    ) {
        val builder = StateBuilder<State, SubState, Action, Effect>()
        builder.block()

        // Action handlers
        actionHandlers.getOrPut(SubState::class, ::LinkedHashMap).putAll(builder.handlers)

        // State lifecycle hooks
        builder.enterHandler?.let { enterHandlers[SubState::class] = it }
        builder.exitHandler?.let { exitHandlers[SubState::class] = it }
        builder.updateHandler?.let { updateHandlers[SubState::class] = it }

        // Application lifecycle hooks
        if (builder.lifecycleHandlers.isNotEmpty()) {
            lifecycleHandlers
                .getOrPut(SubState::class, ::LinkedHashMap)
                .putAll(builder.lifecycleHandlers)
        }

        // Error recovery hook
        builder.errorHandler?.let { errorHandlers[SubState::class] = it }
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Snapshot the current builder state into a [dev.gmvalentino.monaka.dsl.StateMachine].
     *
     * @param initialState When non-null, replaces any state set via [StateMachineBuilder.initialState].
     *   When both this parameter and the builder's [initialState] call are omitted, the resulting
     *   [StateMachine.initialState] is `null` — the caller must supply a non-null state when
     *   creating the store (e.g. via [store]).
     */
    public fun build(
        initialState: State? = null,
    ): StateMachine<State, Action, Effect> {
        val snapshotId = id
        val snapshotName = name
        val snapshotInitialState: State? = initialState ?: this.initialState
        val snapshotActionHandlers = actionHandlers.mapValuesTo(LinkedHashMap()) { LinkedHashMap(it.value) }
        val snapshotEnterHandlers = LinkedHashMap(enterHandlers)
        val snapshotExitHandlers = LinkedHashMap(exitHandlers)
        val snapshotUpdateHandlers = LinkedHashMap(updateHandlers)
        val snapshotLifecycleHandlers = lifecycleHandlers.mapValuesTo(LinkedHashMap()) { LinkedHashMap(it.value) }
        val snapshotErrorHandlers = LinkedHashMap(errorHandlers)
        return object : StateMachine<State, Action, Effect> {
            override val id = snapshotId
            override val name = snapshotName
            override val initialState: State? = snapshotInitialState
            override val actionHandlers = snapshotActionHandlers
            override val enterHandlers = snapshotEnterHandlers
            override val exitHandlers = snapshotExitHandlers
            override val updateHandlers = snapshotUpdateHandlers
            override val lifecycleHandlers = snapshotLifecycleHandlers
            override val errorHandlers = snapshotErrorHandlers
        }
    }
}
