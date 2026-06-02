package tech.fika.monaka.binder

import kotlin.reflect.KClass
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store


/**
 * Create a [Binder] that wires stores of class [from] to stores of class [to].
 *
 * ### Example
 * ```kotlin
 * val authToSessionBinder = binder(
 *     from = AuthStateMachine::class,
 *     to = SessionStateMachine::class,
 * ) {
 *     bindState<AuthState.SignedOut>          { SessionAction.Invalidate }
 *     bindEffect<AuthEffect.TokenRefreshed>   { SessionAction.Refresh }
 *     bindAction<AuthAction.SignOut>          { SessionAction.Invalidate }
 * }
 *
 * registry.install(authToSessionBinder)
 * ```
 *
 * @param from  KClass of the source store type.
 * @param to    KClass of the target store type.
 * @param builder DSL block to configure state, effect, and action transforms.
 */
fun <SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker, TargetAction : ActionMarker> binder(
    from: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    to: KClass<out Store<*, TargetAction, *>>,
    builder: BinderBuilder<SourceState, SourceAction, SourceEffect, TargetAction>.() -> Unit,
): Binder<SourceState, SourceAction, SourceEffect, TargetAction> =
    BinderBuilder<SourceState, SourceAction, SourceEffect, TargetAction>().apply(builder).build(from, to)
