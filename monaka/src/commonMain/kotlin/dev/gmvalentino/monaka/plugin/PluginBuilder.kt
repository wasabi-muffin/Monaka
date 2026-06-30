package dev.gmvalentino.monaka.plugin

import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.handler.HandlerType
import kotlin.jvm.JvmName

/**
 * DSL builder for constructing a [Plugin] from individual hook lambdas.
 *
 * Obtain an instance via the [plugin] top-level factory function. Only register the hooks
 * you need — all others default to no-ops. Multiple registrations of the same hook are
 * supported and fire in registration order.
 *
 * ### Unfiltered hooks
 * ```kotlin
 * val myPlugin = plugin {
 *     onAction {
 *         println("→ $action in $currentState")
 *     }
 *     onTransition {
 *         println("$fromState → $toState")
 *     }
 *     onError {
 *         crashReporter.record(error)
 *     }
 * }
 * ```
 *
 * ### Type-filtered hooks
 * Pass a type argument to receive only events matching that specific type.
 * The scope's typed property (`action`, `effect`, `toState`, `error`) is cast to that type.
 * ```kotlin
 * val myPlugin = plugin {
 *     onAction<LoginAction.Submit> {
 *         analytics.trackLogin(action.username)   // action is LoginAction.Submit
 *     }
 *     onEffect<LoginEffect.NavigateToHome> {
 *         navigator.navigate(effect.destination)  // effect is LoginEffect.NavigateToHome
 *     }
 *     onTransition<LoginState.Authenticated> {
 *         println("entered $toState")             // toState is LoginState.Authenticated
 *     }
 *     onError<NetworkException> {
 *         logger.warn("Network error: ${error.message}")  // error is NetworkException
 *     }
 * }
 * ```
 *
 * Hooks match the [Plugin] interface exactly — see its KDoc for the semantics of each callback.
 */
@MonakaDsl
public class PluginBuilder {

    @PublishedApi internal val onActionHandlers: MutableList<(State, Action) -> Unit> = mutableListOf()

    @PublishedApi internal val onEffectHandlers: MutableList<(Effect) -> Unit> = mutableListOf()

    @PublishedApi internal val onTransitionHandlers: MutableList<(State, State) -> Unit> = mutableListOf()

    @PublishedApi internal val onUnhandledHandlers: MutableList<(State, Action) -> Unit> = mutableListOf()

    @PublishedApi internal val onErrorHandlers: MutableList<(Throwable, State, HandlerType<Action>) -> Unit> = mutableListOf()
    private val onRejectedHandlers: MutableList<(State, HandlerType<Action>) -> Unit> = mutableListOf()

    public class ActionScope<out A : Action> @PublishedApi internal constructor(public val currentState: State, public val action: A)
    public class EffectScope<out E : Effect> @PublishedApi internal constructor(public val effect: E)
    public class TransitionScope<out S : State> @PublishedApi internal constructor(public val fromState: State, public val toState: S)
    public class UnhandledScope<out A : Action> @PublishedApi internal constructor(public val currentState: State, public val action: A)
    public class ErrorScope<out E : Throwable> @PublishedApi internal constructor(public val error: E, public val currentState: State, public val handlerType: HandlerType<Action>)
    public class RejectedScope internal constructor(public val currentState: State, public val handlerType: HandlerType<Action>)

    /** Register a callback for [Plugin.onAction], fired for every action. */
    public fun onAction(block: ActionScope<Action>.() -> Unit) {
        onActionHandlers.add { currentState, action -> ActionScope(currentState, action).block() }
    }

    /** Register a callback for [Plugin.onAction], fired only when the action is of type [A]. */
    @JvmName("onActionTyped")
    public inline fun <reified A : Action> onAction(crossinline block: ActionScope<A>.() -> Unit) {
        onActionHandlers.add { currentState, action ->
            if (action is A) ActionScope(currentState, action).block()
        }
    }

    /** Register a callback for [Plugin.onEffect], fired for every effect. */
    public fun onEffect(block: EffectScope<Effect>.() -> Unit) {
        onEffectHandlers.add { effect -> EffectScope(effect).block() }
    }

    /** Register a callback for [Plugin.onEffect], fired only when the effect is of type [E]. */
    @JvmName("onEffectTyped")
    public inline fun <reified E : Effect> onEffect(crossinline block: EffectScope<E>.() -> Unit) {
        onEffectHandlers.add { effect ->
            if (effect is E) EffectScope(effect).block()
        }
    }

    /** Register a callback for [Plugin.onTransition], fired for every transition. */
    public fun onTransition(block: TransitionScope<State>.() -> Unit) {
        onTransitionHandlers.add { fromState, toState -> TransitionScope(fromState, toState).block() }
    }

    /**
     * Register a callback for [Plugin.onTransition], fired only when [toState][TransitionScope.toState]
     * is of type [S].
     */
    @JvmName("onTransitionTyped")
    public inline fun <reified S : State> onTransition(crossinline block: TransitionScope<S>.() -> Unit) {
        onTransitionHandlers.add { fromState, toState ->
            if (toState is S) TransitionScope(fromState, toState).block()
        }
    }

    /** Register a callback for [Plugin.onUnhandled], fired for every unhandled action. */
    public fun onUnhandled(block: UnhandledScope<Action>.() -> Unit) {
        onUnhandledHandlers.add { currentState, action -> UnhandledScope(currentState, action).block() }
    }

    /** Register a callback for [Plugin.onUnhandled], fired only when the action is of type [A]. */
    @JvmName("onUnhandledTyped")
    public inline fun <reified A : Action> onUnhandled(crossinline block: UnhandledScope<A>.() -> Unit) {
        onUnhandledHandlers.add { currentState, action ->
            if (action is A) UnhandledScope(currentState, action).block()
        }
    }

    /** Register a callback for [Plugin.onError], fired for every error. */
    public fun onError(block: ErrorScope<Throwable>.() -> Unit) {
        onErrorHandlers.add { error, currentState, handlerType -> ErrorScope(error, currentState, handlerType).block() }
    }

    /** Register a callback for [Plugin.onError], fired only when the error is of type [E]. */
    @JvmName("onErrorTyped")
    public inline fun <reified E : Throwable> onError(crossinline block: ErrorScope<E>.() -> Unit) {
        onErrorHandlers.add { error, currentState, handlerType ->
            if (error is E) ErrorScope(error, currentState, handlerType).block()
        }
    }

    /** Register a callback for [Plugin.onRejected]. */
    public fun onRejected(block: RejectedScope.() -> Unit) {
        onRejectedHandlers.add { currentState, handlerType -> RejectedScope(currentState, handlerType).block() }
    }

    internal fun build(): Plugin = object : Plugin {
        override fun onAction(currentState: State, action: Action) = this@PluginBuilder.onActionHandlers.forEach { handler -> handler(currentState, action) }

        override fun onEffect(effect: Effect) = this@PluginBuilder.onEffectHandlers.forEach { handler -> handler(effect) }

        override fun onTransition(fromState: State, toState: State) = this@PluginBuilder.onTransitionHandlers.forEach { handler -> handler(fromState, toState) }

        override fun onUnhandled(currentState: State, action: Action) = this@PluginBuilder.onUnhandledHandlers.forEach { handler -> handler(currentState, action) }

        override fun onRejected(currentState: State, handlerType: HandlerType<Action>) = this@PluginBuilder.onRejectedHandlers.forEach { handler -> handler(currentState, handlerType) }

        override fun onError(error: Throwable, currentState: State, handlerType: HandlerType<Action>) = this@PluginBuilder.onErrorHandlers.forEach { handler -> handler(error, currentState, handlerType) }
    }
}

/**
 * Create a [Plugin] using the [PluginBuilder] DSL.
 *
 * ```kotlin
 * install(plugin {
 *     onAction<LoginAction.Submit> { analytics.track(action.username) }
 *     onError<NetworkException>   { crashReporter.record(error) }
 * })
 *
 * // Or inside a StoreRegistry initializer with per-store context:
 * StoreRegistry {
 *     install {
 *         plugin {
 *             onTransition { println("[${store.name}] $fromState → $toState") }
 *         }
 *     }
 * }
 * ```
 */
public fun plugin(block: PluginBuilder.() -> Unit): Plugin = PluginBuilder().apply(block).build()
