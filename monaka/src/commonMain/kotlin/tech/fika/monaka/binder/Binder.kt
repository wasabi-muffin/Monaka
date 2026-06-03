package tech.fika.monaka.binder

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store

/**
 * Encapsulates the mapping rules between a source and a target store class.
 *
 * A [Binder] is installed into a [tech.fika.monaka.runtime.StoreRegistry] via [tech.fika.monaka.runtime.StoreRegistry.install] and
 * applied automatically whenever a matching source or target store is registered.
 * It can forward state changes, effects, and/or dispatched actions from every instance
 * of the [source] class to every instance of the [target] class.
 *
 * Create instances with the [binder] factory:
 * ```kotlin
 * val authToCartBinder = binder(
 *     from = AuthStateMachine::class,
 *     to = CartStateMachine::class,
 * ) {
 *     bindState<AuthState.SignedIn>  { CartAction.LoadForUser(user.id) }
 *     bindState<AuthState.SignedOut> { CartAction.Clear }
 *     bindEffect<AuthEffect.TokenExpired> { CartAction.Clear }
 * }
 *
 * registry.install(authToCartBinder)
 * ```
 *
 * Implement this interface directly when the [binder] DSL is insufficient, such as
 * when binding logic requires injected dependencies or more complex coordination.
 */
interface Binder<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker, TargetAction : ActionMarker> {
    val source: KClass<out Store<SourceState, SourceAction, SourceEffect>>
    val target: KClass<out Store<*, TargetAction, *>>
    /**
     * Wire [source] to [target] by launching collector coroutines in [scope].
     *
     * Returns the launched [Job]s so the caller can cancel them when the source
     * or target store is unregistered.
     */
    fun apply(source: Store<*, *, *>, target: Store<*, *, *>, scope: CoroutineScope): List<Job>
}
