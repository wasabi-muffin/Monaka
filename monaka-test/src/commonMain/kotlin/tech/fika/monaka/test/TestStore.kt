package tech.fika.monaka.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.StateMachine

/**
 * Run one or more test cases against [machine].
 *
 * Each [TestStoreScope.testCase] block runs sequentially under a single `runTest`
 * but constructs its own [Store] from [machine], so test cases are behaviorally isolated.
 *
 * ### Example
 * ```kotlin
 * @Test
 * fun loginFlow() = testStore(machine = loginMachine) {
 *     testCase("submit drives Idle → Submitting → Authenticated") {
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
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> testStore(
    machine: StateMachine<State, Action, Effect>,
    body: suspend TestStoreScope<State, Action, Effect>.() -> Unit,
): TestResult = runTest {
    val scope = TestStoreScope(machine = machine, testScope = this)
    scope.body()
}
