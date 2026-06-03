package tech.fika.monaka.test

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.store

/**
 * Run one or more scenarios against [machine].
 *
 * Each [TestStoreScope.scenario] block runs sequentially under a single `runTest`
 * but constructs its own [Store] from [machine], so scenarios are behaviorally isolated.
 *
 * ### Example
 * ```kotlin
 * @Test
 * fun loginFlow() = testStore(machine = loginMachine) {
 *     scenario("submit drives Idle → Submitting → Authenticated") {
 *         given(LoginState.Idle(username = "x", password = "y"))
 *
 *         trigger(LoginAction.Submit) {
 *             expectState<LoginState.Submitting>()
 *             expectEffect(LoginEffect.HideKeyboard)
 *         }
 *
 *         expectIdle()
 *     }
 * }
 * ```
 */
fun <S : StateMarker, A : ActionMarker, E : EffectMarker> testStore(
    machine: StateMachine<S, A, E>,
    body: suspend TestStoreScope<S, A, E>.() -> Unit,
): TestResult = runTest {
    val scope = TestStoreScope(machine = machine, testScope = this)
    scope.body()
}

@MonakaTestDsl
class TestStoreScope<S : StateMarker, A : ActionMarker, E : EffectMarker> internal constructor(
    private val machine: StateMachine<S, A, E>,
    private val testScope: TestScope,
) {
    /**
     * Run [body] against a freshly constructed [Store] for this scenario.
     *
     * Each scenario is isolated: it builds its own store from the shared [StateMachine]
     * configuration, sets up its own Turbines, and tears them down on completion.
     */
    suspend fun scenario(
        name: String,
        exhaustive: Boolean = true,
        body: suspend ScenarioBuilder<S, A, E>.() -> Unit,
    ) {
        turbineScope {
            val builder = ScenarioBuilder(
                name = name,
                machine = machine,
                testScope = testScope,
                turbineScope = this,
            )
            var bodyFailed = false
            try {
                builder.body()
            } catch (t: Throwable) {
                bodyFailed = true
                throw t
            } finally {
                // Only assert idle when the body succeeded — otherwise a follow-up
                // failure here would mask the real assertion error.
                if (exhaustive && !bodyFailed && builder.isStarted) {
                    builder.expectIdle()
                }
                builder.dispose()
            }
        }
    }
}

@MonakaTestDsl
class ScenarioBuilder<S : StateMarker, A : ActionMarker, E : EffectMarker> internal constructor(
    @Suppress("unused") private val name: String,
    private val machine: StateMachine<S, A, E>,
    private val testScope: TestScope,
    private val turbineScope: CoroutineScope,
) {
    private var initialState: S? = null

    private var store: Store<S, A, E>? = null
    private var stateTurbine: ReceiveTurbine<S>? = null
    private var effectTurbine: ReceiveTurbine<E>? = null
    private var actionTurbine: ReceiveTurbine<A>? = null

    /**
     * Tracks actions dispatched by the test so the action stream can filter them
     * out — only handler-initiated actions surface to [AssertScope.expectAction].
     *
     * Mutated only from the test thread (single-threaded under `runTest`).
     */
    private val externalActions: ArrayDeque<A> = ArrayDeque()

    private var subscribersReady: Boolean = false

    internal val isStarted: Boolean get() = store != null

    /**
     * Override the initial state declared in the [StateMachine].
     *
     * Must be called before the first [trigger] or [expectIdle].
     */
    fun given(state: S) {
        check(store == null) { "given(...) must be called before the store starts" }
        initialState = state
    }

    /**
     * Dispatch [action] to the store and run [block] to assert on subsequent emissions.
     */
    suspend fun trigger(
        action: A,
        block: suspend AssertScope<S, A, E>.() -> Unit = {},
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
        block: suspend AssertScope<S, A, E>.() -> Unit = {},
    ) {
        val s = ensureStartedAndSubscribed()
        s.onLifecycleEvent(event)
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

    private suspend fun ensureStartedAndSubscribed(): Store<S, A, E> {
        val s = ensureStarted()
        if (!subscribersReady) {
            // Give the filter collector + turbine collectors a chance to subscribe
            // before any dispatch fires, otherwise replay=0 SharedFlows drop emissions.
            testScope.testScheduler.runCurrent()
            subscribersReady = true
        }
        return s
    }

    private fun ensureStarted(): Store<S, A, E> {
        store?.let { return it }
        val s = store(
            stateMachine = machine,
            scope = testScope.backgroundScope,
            initialState = initialState,
        )
        store = s

        val reentrant = MutableSharedFlow<A>(extraBufferCapacity = Channel.UNLIMITED)
        startActionFilter(scope = testScope.backgroundScope, source = s, sink = reentrant)

        stateTurbine = s.state.drop(count = 1).testIn(scope = turbineScope)
        effectTurbine = s.effects.testIn(scope = turbineScope)
        actionTurbine = reentrant.testIn(scope = turbineScope)
        return s
    }

    private fun startActionFilter(
        scope: CoroutineScope,
        source: Store<S, A, E>,
        sink: MutableSharedFlow<A>,
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
