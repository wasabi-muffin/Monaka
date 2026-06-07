package tech.fika.monaka.test

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.testIn
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.StateHook
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.store
import tech.fika.monaka.plugin.Plugin

@MonakaTestDsl
class TestCaseBuilder<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> internal constructor(
    @Suppress("unused") private val name: String,
    private val machine: StateMachine<State, Action, Effect>,
    private val testScope: TestScope,
    private val turbineScope: CoroutineScope,
) {
    private var initialState: State? = null

    private var store: Store<State, Action, Effect>? = null
    private var stateTurbine: ReceiveTurbine<State>? = null
    private var effectTurbine: ReceiveTurbine<Effect>? = null
    private var actionTurbine: ReceiveTurbine<Action>? = null

    /**
     * Tracks actions dispatched by the test so the action stream can filter them
     * out — only handler-initiated actions surface to [AssertScope.expectAction].
     *
     * Mutated only from the test thread (single-threaded under `runTest`).
     */
    private val externalActions: ArrayDeque<Action> = ArrayDeque()

    private var subscribersReady: Boolean = false

    internal val isStarted: Boolean get() = store != null

    internal var isFinished: Boolean = false
        private set

    /**
     * Mark this test case as complete and skip the automatic [expectIdle] check.
     *
     * Use when the test case intentionally leaves work pending — in-flight `task { }`
     * coroutines, queued effects, or unobserved state changes — that you do not care to
     * drain or assert on. After `finish()` returns, the test case body continues normally;
     * place the call as the last statement (or follow it with `return@testCase`) if you
     * want execution to stop there.
     *
     * Prefer `exhaustive = false` on the test case declaration itself when you know upfront
     * that the test case will leave residue; reach for `finish()` when the decision is
     * runtime-conditional.
     */
    fun finish() {
        isFinished = true
    }

    /**
     * Override the initial state declared in the [StateMachine].
     *
     * Must be called before the first [trigger] or [expectIdle].
     */
    fun given(state: State) {
        check(store == null) { "given(...) must be called before the store starts" }
        initialState = state
    }

    /**
     * Dispatch [action] to the store and run [block] to assert on subsequent emissions.
     */
    suspend fun trigger(
        action: Action,
        block: suspend AssertScope<State, Action, Effect>.() -> Unit = {},
    ) {
        val s = ensureStartedAndSubscribed()
        externalActions.addLast(action)
        s.dispatch(action)
        testScope.testScheduler.runCurrent()
        AssertScope(stateTurbine!!, effectTurbine!!, actionTurbine!!).block()
    }

    /**
     * Forward a lifecycle [event] to the store and run [block] to assert on subsequent emissions.
     */
    suspend fun trigger(
        event: LifecycleEvent,
        block: suspend AssertScope<State, Action, Effect>.() -> Unit = {},
    ) {
        val s = ensureStartedAndSubscribed()
        s.onLifecycleEvent(event)
        testScope.testScheduler.runCurrent()
        AssertScope(stateTurbine!!, effectTurbine!!, actionTurbine!!).block()
    }

    /**
     * Fire a state-lifecycle [hook] for the current state and run [block] to assert on
     * subsequent emissions.
     *
     * - [StateHook.OnEnter] fires the `onEnter { }` handler registered for the current state.
     * - [StateHook.OnExit] fires the `onExit { }` handler registered for the current state.
     * - [StateHook.OnUpdate] fires the `onUpdate { }` handler registered for the current state.
     */
    suspend fun trigger(
        hook: StateHook,
        block: suspend AssertScope<State, Action, Effect>.() -> Unit = {},
    ) {
        val s = ensureStartedAndSubscribed()
        s.triggerStateHook(hook)
        testScope.testScheduler.runCurrent()
        AssertScope(stateTurbine!!, effectTurbine!!, actionTurbine!!).block()
    }

    /**
     * Advance virtual time by [duration] and run [block] to assert on subsequent emissions.
     *
     * Use this to drive time-based behaviour such as `delay`-backed tickers or debounces
     * that live inside `task { }` handlers. Unlike [trigger], no action is dispatched —
     * the store advances purely because time passed.
     *
     * After advancing the clock, [runCurrent][kotlinx.coroutines.test.TestCoroutineScheduler.runCurrent]
     * is called so that any coroutines scheduled at exactly the new virtual time — including
     * tasks that were launched by other tasks reaching their deadline at the same instant —
     * run before the assertion [block] executes.
     */
    suspend fun advanceTime(
        duration: Duration,
        block: suspend AssertScope<State, Action, Effect>.() -> Unit = {},
    ) {
        ensureStartedAndSubscribed()
        testScope.testScheduler.advanceTimeBy(duration)
        testScope.testScheduler.runCurrent()
        AssertScope(stateTurbine!!, effectTurbine!!, actionTurbine!!).block()
    }

    /**
     * Assert no further emissions are pending on any stream.
     */
    suspend fun expectIdle() {
        ensureStartedAndSubscribed()
        testScope.testScheduler.runCurrent()
        stateTurbine!!.expectNoEvents()
        effectTurbine!!.expectNoEvents()
        actionTurbine!!.expectNoEvents()
        check(externalActions.isEmpty()) {
            "Test-issued actions never observed on store.actions: $externalActions"
        }
    }

    private suspend fun ensureStartedAndSubscribed(): Store<State, Action, Effect> {
        val s = ensureStarted()
        if (!subscribersReady) {
            // Give the filter collector + turbine collectors a chance to subscribe
            // before any dispatch fires, otherwise replay=0 SharedFlows drop emissions.
            testScope.testScheduler.runCurrent()
            subscribersReady = true
        }
        return s
    }

    private fun ensureStarted(): Store<State, Action, Effect> {
        store?.let { return it }

        // Capture every state transition through a plugin rather than observing the
        // StateFlow directly. StateFlow conflates rapid sequential updates — if two
        // state transitions happen before the collector coroutine is scheduled, only
        // the final value is delivered. A SharedFlow with an UNLIMITED buffer retains
        // every emission in arrival order with no conflation.
        val stateTransitions = MutableSharedFlow<State>(extraBufferCapacity = Channel.UNLIMITED)
        val statePlugin = object : Plugin<State, Action, Effect> {
            override fun onTransition(fromState: State, toState: State) {
                stateTransitions.tryEmit(toState)
            }
        }

        val testStore = store(
            stateMachine = machine,
            scope = testScope.backgroundScope,
            initialState = initialState,
            plugins = listOf(statePlugin),
        )
        store = testStore

        val reentrant = MutableSharedFlow<Action>(extraBufferCapacity = Channel.UNLIMITED)
        startActionFilter(scope = testScope.backgroundScope, source = testStore, sink = reentrant)

        stateTurbine = stateTransitions.testIn(scope = turbineScope)
        effectTurbine = testStore.effects.testIn(scope = turbineScope)
        actionTurbine = reentrant.testIn(scope = turbineScope)
        testStore.start()
        return testStore
    }

    private fun startActionFilter(
        scope: CoroutineScope,
        source: Store<State, Action, Effect>,
        sink: MutableSharedFlow<Action>,
    ) {
        scope.launch {
            source.actions.collect { action ->
                if (externalActions.isNotEmpty() && externalActions.first() == action) {
                    externalActions.removeFirst()
                } else {
                    sink.emit(action)
                }
            }
        }
    }

    internal suspend fun dispose() {
        stateTurbine?.cancelAndIgnoreRemainingEvents()
        effectTurbine?.cancelAndIgnoreRemainingEvents()
        actionTurbine?.cancelAndIgnoreRemainingEvents()
        store?.cancel()
    }
}
