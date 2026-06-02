package tech.fika.monaka.dsl

import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker

/**
 * Build an immutable [StateMachine] configuration without starting it.
 *
 * Use this when you want to share or inspect a machine definition before
 * running it, or when you need to start multiple instances from the same config:
 *
 * ```kotlin
 * val loginMachine = stateMachine<LoginState, LoginAction, LoginEffect> {
 *     initialState(LoginState.Idle)
 *     state<LoginState.Idle> { … }
 * }
 *
 * // Start two independent instances:
 * val store1 = store(loginMachine, scope1)
 * val store2 = store(loginMachine, scope2, initialState = LoginState.Typing("bob"))
 * ```
 */
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> stateMachine(
    builder: StateMachineBuilder<State, Action, Effect>.() -> Unit,
): StateMachine<State, Action, Effect> = StateMachineBuilder<State, Action, Effect>().apply(builder).build()
