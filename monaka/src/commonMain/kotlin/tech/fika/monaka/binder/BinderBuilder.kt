package tech.fika.monaka.binder

import kotlin.reflect.KClass
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.MonakaDsl

/**
 * Builder for [Binder]. Configure which source events should be forwarded to the target.
 *
 * Any combination of [bindState], [bindEffect], and [bindAction] can be called, including
 * multiple times for different subtypes. Events with no binding configured are not forwarded.
 */
@MonakaDsl
class BinderBuilder<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker, TargetAction : ActionMarker> {

    @PublishedApi
    internal val stateTransforms = mutableListOf<(SourceState) -> TargetAction?>()

    @PublishedApi
    internal val effectTransforms = mutableListOf<(SourceEffect) -> TargetAction?>()

    @PublishedApi
    internal val actionTransforms = mutableListOf<(SourceAction) -> TargetAction?>()

    /**
     * Forward source states of type [BindingState] to the target.
     *
     * The lambda runs with the matched state as its receiver and returns the target action,
     * or `null` to skip. May be called multiple times for different subtypes.
     *
     * ```kotlin
     * bindState<AuthState.SignedIn> { CartAction.LoadForUser(user.id) }
     * bindState<AuthState.SignedOut> { CartAction.Clear }
     * ```
     */
    inline fun <reified BindingState : SourceState> bindState(crossinline transform: BindingState.() -> TargetAction?) {
        stateTransforms.add { state -> (state as? BindingState)?.transform() }
    }

    /**
     * Forward source effects of type [BindingEffect] to the target.
     *
     * The lambda runs with the matched effect as its receiver and returns the target action,
     * or `null` to skip. May be called multiple times for different subtypes.
     *
     * ```kotlin
     * bindEffect<CartEffect.CartChanged> { CheckoutAction.SyncCart(items, total) }
     * ```
     */
    inline fun <reified BindingEffect : SourceEffect> bindEffect(crossinline transform: BindingEffect.() -> TargetAction?) {
        effectTransforms.add { effect -> (effect as? BindingEffect)?.transform() }
    }

    /**
     * Forward source actions of type [BindingAction] to the target.
     *
     * The lambda runs with the matched action as its receiver and returns the target action,
     * or `null` to skip. May be called multiple times for different subtypes.
     *
     * ```kotlin
     * bindAction<AuthAction.SignOut> { SessionAction.Invalidate }
     * ```
     */
    inline fun <reified BindingAction : SourceAction> bindAction(crossinline transform: BindingAction.() -> TargetAction?) {
        actionTransforms.add { action -> (action as? BindingAction)?.transform() }
    }

    internal fun build(
        from: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
        to: KClass<out Store<*, TargetAction, *>>,
    ): Binder<SourceState, SourceAction, SourceEffect, TargetAction> = DefaultBinder(
        source = from,
        target = to,
        stateTransform = stateTransforms.mergeOrNull(),
        effectTransform = effectTransforms.mergeOrNull(),
        actionTransform = actionTransforms.mergeOrNull(),
    )

    /**
     * Collapses a list of `(T) -> R?` transforms into a single function that returns the first
     * non-null result, or returns `null` when the list is empty (so no collection coroutine is
     * launched for that channel).
     */
    private fun <Source> List<(Source) -> TargetAction?>.mergeOrNull(): ((Source) -> TargetAction?)? =
        if (isEmpty()) null else { value -> firstNotNullOfOrNull { transformation -> transformation(value) } }
}
