package dev.gmvalentino.monaka.plugin

import dev.gmvalentino.monaka.handler.HandlerType
import co.touchlab.kermit.Logger as Kermit
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
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
 * store<State, Action, Effect>(scope) {
 *     // …
 *     install(LoggingPlugin(tag = "Auth"))
 *     // with custom logger:
 *     install(LoggingPlugin(tag = "Auth") { tag, message -> Log.d(tag, message) })
 * }
 * ```
 *
 * Or globally via [dev.gmvalentino.monaka.runtime.StoreRegistry] to tag each store by name:
 * ```kotlin
 * StoreRegistry(viewModelScope) {
 *     install { LoggingPlugin(tag = name) }
 * }
 * ```
 *
 * Sample output:
 * ```
 * [ACTION]     LoginAction.Submit
 * [TRANSITION] LoginState.Submitting
 * [EFFECT]     LoginEffect.NavigateToHome
 * [UNHANDLED]  LoginAction.Logout  (state: Authenticated)
 * [ERROR]      IllegalStateException: token expired  (handler: Hook.Enter)
 * ```
 */
public class LoggingPlugin(
    private val tag: String = "Monaka",
    private val logger: Logger = Logger { tag, message -> Kermit.d(tag = tag) { message } },
) : Plugin {

    override fun onAction(currentState: StateMarker, action: ActionMarker) {
        logger.log(tag = tag, message = "[ACTION]     $action")
    }

    override fun onTransition(fromState: StateMarker, toState: StateMarker) {
        if (fromState != toState) {
            logger.log(tag = tag, message = "[TRANSITION] $toState")
        }
    }

    override fun onEffect(effect: EffectMarker) {
        logger.log(tag = tag, message = "[EFFECT]     $effect")
    }

    override fun onUnhandled(currentState: StateMarker, action: ActionMarker) {
        logger.log(tag = tag, message = "[UNHANDLED]  $action  (state: ${currentState::class.simpleName})")
    }

    override fun onRejected(currentState: StateMarker, handlerType: HandlerType<ActionMarker>) {
        logger.log(tag = tag, message = "[REJECTED]   $handlerType  (state: ${currentState::class.simpleName})")
    }

    override fun onError(error: Throwable, currentState: StateMarker, handlerType: HandlerType<ActionMarker>) {
        logger.log(tag = tag, message = "[ERROR]      ${error::class.simpleName}: ${error.message}  (handler: $handlerType)")
    }
}

/** Receives formatted log lines from [LoggingPlugin]. Implement to redirect output to a platform logger. */
public fun interface Logger {
    /** Write [message] to the log output. [tag] is the prefix configured on [LoggingPlugin]. */
    public fun log(tag: String, message: String)
}
