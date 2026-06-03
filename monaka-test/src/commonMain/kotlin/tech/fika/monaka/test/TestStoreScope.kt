package tech.fika.monaka.test

import app.cash.turbine.turbineScope
import io.kotest.assertions.withClue
import kotlinx.coroutines.test.TestScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.dsl.StateMachine

@MonakaTestDsl
class TestStoreScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> internal constructor(
    private val machine: StateMachine<State, Action, Effect>,
    private val testScope: TestScope,
) {
    /**
     * Run [body] against a freshly constructed [tech.fika.monaka.core.Store] for this test case.
     *
     * Each test case is isolated: it builds its own store from the shared [StateMachine]
     * configuration, sets up its own Turbines, and tears them down on completion.
     */
    suspend fun testCase(
        name: String,
        exhaustive: Boolean = true,
        body: suspend TestCaseBuilder<State, Action, Effect>.() -> Unit,
    ) {
        turbineScope {
            val builder = TestCaseBuilder(
                name = name,
                machine = machine,
                testScope = testScope,
                turbineScope = this,
            )
            var bodyFailed = false
            try {
                withClue("Test case: $name") { builder.body() }
            } catch (t: Throwable) {
                bodyFailed = true
                throw t
            } finally {
                // Only assert idle when the body succeeded — otherwise a follow-up
                // failure here would mask the real assertion error.
                if (exhaustive && !bodyFailed && !builder.isFinished && builder.isStarted) {
                    builder.expectIdle()
                }
                builder.dispose()
            }
        }
    }
}
