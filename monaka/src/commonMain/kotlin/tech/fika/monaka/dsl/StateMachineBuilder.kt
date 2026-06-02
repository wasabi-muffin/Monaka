@file:OptIn(ExperimentalUuidApi::class)

package tech.fika.monaka.dsl

import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.error.DefaultErrorMapper
import tech.fika.monaka.error.ErrorMapper
import tech.fika.monaka.handler.ActionHandler
import tech.fika.monaka.handler.LifecycleHandler
import tech.fika.monaka.handler.StateChangeHandler
import tech.fika.monaka.handler.StateErrorHandler
import tech.fika.monaka.handler.StateUpdateHandler
import tech.fika.monaka.plugin.Plugin

/**
 * Root DSL builder for constructing a [tech.fika.monaka.dsl.StateMachineStore] specification.
 *
 * Obtain an instance via the [tech.fika.monaka.dsl.store] top-level factory function.
 *
 * ### Handler lookup order
 * When an action is dispatched, the runtime resolves a handler as follows:
 * 1. Look for a handler registered under the **exact runtime class** of the current state.
 * 2. Walk up the supertype hierarchy (BFS), checking each ancestor class in order
 *    of proximity. The closest registered ancestor wins.
 * 3. If nothing matches, notify plugins via [Plugin.onInvalid] and skip.
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
 *     on<MyAction.Reset> { transition { MyState.Idle } }
 * }
 *
 * state<MyState.Loading> {                // exact match — takes priority for its actions
 *     on<MyAction.Cancel> { … }
 *     on<MyAction.Reset>  { … }          // overrides the parent's Reset for Loading only
 * }
 * ```
 */
@MonakaDsl
class StateMachineBuilder<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {

    /** Unique identifier for the machine being built. */
    val id: String = Uuid.random().toString()

    internal var initialState: State? = null

    // state-class → (action-class → handler)
    @PublishedApi
    internal val actionHandlers: LinkedHashMap<KClass<*>, LinkedHashMap<KClass<*>, ActionHandler<State, Action, Effect>>> =
        LinkedHashMap()

    // state-class → enter / exit / modify hooks
    @PublishedApi
    internal val enterHandlers: LinkedHashMap<KClass<*>, StateChangeHandler<State, Action, Effect>> = LinkedHashMap()

    @PublishedApi
    internal val exitHandlers: LinkedHashMap<KClass<*>, StateChangeHandler<State, Action, Effect>> = LinkedHashMap()

    @PublishedApi
    internal val updateHandlers: LinkedHashMap<KClass<*>, StateUpdateHandler<State, Action, Effect>> = LinkedHashMap()

    // state-class → (lifecycle-event → hook)
    @PublishedApi
    internal val lifecycleHandlers: LinkedHashMap<KClass<*>, LinkedHashMap<LifecycleEvent, LifecycleHandler<State, Action, Effect>>> =
        LinkedHashMap()

    // state-class → error recovery hook
    @PublishedApi
    internal val errorHandlers: LinkedHashMap<KClass<*>, StateErrorHandler<State, Action, Effect>> = LinkedHashMap()

    internal var errorMapper: ErrorMapper = DefaultErrorMapper

    internal val plugins: MutableList<Plugin<State, Action, Effect>> = mutableListOf()

    // ── Required configuration ────────────────────────────────────────────────

    /**
     * Set the initial state of the state machine. **Required.**
     *
     * Must be called exactly once before the builder block returns.
     */
    fun initialState(state: State) {
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
    inline fun <reified SubState : State> state(
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

    // ── Plugin installation ───────────────────────────────────────────────────

    // ── Error mapper ──────────────────────────────────────────────────────────

    /**
     * Override the machine-level [ErrorMapper] used to convert raw [Throwable]s into
     * [tech.fika.monaka.error.AppError] before passing them to `onError` hooks and
     * [tech.fika.monaka.plugin.Plugin.onError].
     *
     * If not called, [DefaultErrorMapper] is used: [tech.fika.monaka.error.AppError]
     * subtypes pass through unchanged; everything else is wrapped in
     * [tech.fika.monaka.error.UnknownError].
     */
    fun errorMapper(mapper: ErrorMapper) {
        errorMapper = mapper
    }

    // ── Plugin installation ───────────────────────────────────────────────────

    /** Install a single [plugin]. Plugins are invoked in installation order. */
    fun install(plugin: Plugin<State, Action, Effect>) {
        plugins.add(plugin)
    }

    /** Install multiple [plugins] at once. */
    fun install(vararg plugins: Plugin<State, Action, Effect>) {
        this.plugins.addAll(plugins)
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Snapshot the current builder state into a [tech.fika.monaka.dsl.StateMachine].
     *
     * @param initialState When non-null, replaces any state set via [StateMachineBuilder.initialState].
     * @param extraPlugins Appended **after** any plugins already installed in this builder.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun build(
        initialState: State? = null,
        extraPlugins: List<Plugin<State, Action, Effect>> = emptyList(),
    ): StateMachine<State, Action, Effect> = object : StateMachine<State, Action, Effect> {
        private val builder = this@StateMachineBuilder
        override val id = builder.id
        override val initialState = initialState ?: builder.initialState ?: error(message = "StateMachine requires an initialState.")
        override val actionHandlers = builder.actionHandlers
        override val enterHandlers = builder.enterHandlers
        override val exitHandlers = builder.exitHandlers
        override val updateHandlers = builder.updateHandlers
        override val lifecycleHandlers = builder.lifecycleHandlers
        override val errorHandlers = builder.errorHandlers
        override val errorMapper = builder.errorMapper
        override val plugins = builder.plugins + extraPlugins
    }
}
