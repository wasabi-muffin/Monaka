package dev.gmvalentino.monaka.plugin

import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.handler.HandlerType
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * A [Plugin] that logs every state machine event to a configurable output.
 *
 * By default, output goes to [println], which works across all KMP targets.
 * Provide a custom [logger] to redirect to platform-specific logging (Logcat,
 * NSLog, SLF4J, etc.).
 *
 * Install example:
 * ```kotlin
 * stateMachine<State, Action, Effect>(scope) {
 *     // …
 *     install(LoggingPlugin(tag = "Auth"))
 *     // with custom logger:
 *     install(LoggingPlugin(tag = "Auth") { message -> Log.d("Monaka", message) })
 * }
 * ```
 *
 * Sample output:
 * ```
 * [Auth] → ACTION   : LoginAction.Submit
 * [Auth]   IN STATE : LoginState.Typing(username=alice, password=***)
 * [Auth] ← STATE   : LoginState.Typing → LoginState.Loading
 * [Auth]   EFFECT  : LoginEffect.NavigateToHome
 * ```
 */
public class LoggingPlugin<State : StateMarker, Action : ActionMarker, Effect : EffectMarker>(
    private val tag: String = "Monaka",
    private val logger: Logger = Logger { _, message -> println(message) },
) : Plugin<State, Action, Effect> {

    override fun onAction(currentState: State, action: Action) {
        logger.log(tag = tag, message = "[$tag] → ACTION   : $action")
        logger.log(tag = tag, message = "[$tag]   IN STATE : $currentState")
    }

    override fun onTransition(fromState: State, toState: State) {
        if (fromState != toState) {
            logger.log(tag = tag, message = "[$tag] ← STATE   : $fromState → $toState")
        }
    }

    override fun onEffect(effect: Effect) {
        logger.log(tag = tag, message = "[$tag]   EFFECT  : $effect")
    }

    override fun onRejected(currentState: State, handlerType: HandlerType<Action>) {
        logger.log(tag = tag, message = "[$tag] ⚠ UNHANDLED: $handlerType  (state: ${currentState::class.simpleName})")
    }

    override fun onError(error: Throwable, currentState: State, handlerType: HandlerType<Action>) {
        logger.log(tag = tag, message = "[$tag] ✗ ERROR    : ${error::class.simpleName}: ${error.message}  (handler: $handlerType)")
    }
}

public fun interface Logger {
    public fun log(tag: String, message: String)
}
