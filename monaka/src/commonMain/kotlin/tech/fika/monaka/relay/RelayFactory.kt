package tech.fika.monaka.relay

import kotlin.reflect.KClass
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store

/**
 * Create a [Relay] that observes stores of class [from].
 *
 * ### Example
 * ```kotlin
 * val authRelay = relay(from = AuthStore::class) {
 *     state<AuthState.SignedIn>  { dispatch<CartStore>(CartAction.LoadForUser(event.user.id)) }
 *     state<AuthState.SignedOut> {
 *         dispatch<CartStore>(CartAction.Clear)
 *         dispatch<CheckoutStore>(CheckoutAction.Cancel)
 *     }
 * }
 *
 * registry.install(authRelay)
 * ```
 *
 * @param from    KClass of the source store type.
 * @param builder DSL block to configure state, effect, and action relays.
 */
fun <SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker> relay(
    from: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    builder: RelayBuilder<SourceState, SourceAction, SourceEffect>.() -> Unit,
): Relay<SourceState, SourceAction, SourceEffect> =
    RelayBuilder<SourceState, SourceAction, SourceEffect>().apply(builder).build(from = from)

/**
 * Reified-store variant of [relay]. Identify the source store by type argument instead of [KClass]:
 *
 * ```kotlin
 * registry.install(
 *     relay<AuthStore> {
 *         state<AuthState.SignedIn> { dispatch<CartStore>(CartAction.LoadForUser(event.user.id)) }
 *     }
 * )
 * ```
 *
 * Note: because Kotlin cannot recover a store's state/action/effect types from the store type
 * alone, the `state`/`effect`/`action` blocks here are bound only by the [tech.fika.monaka.core.State]/
 * [tech.fika.monaka.core.Action]/[tech.fika.monaka.core.Effect] markers rather than the source
 * store's specific types — a block for a state the source never emits compiles but never fires.
 * Use the [KClass] overload when you want those blocks type-checked against the source store, or
 * when you need the precisely-typed [Relay] for `by`-delegation.
 */
inline fun <reified S : Store<*, *, *>> relay(
    builder: RelayBuilder<StateMarker, ActionMarker, EffectMarker>.() -> Unit,
): Relay<StateMarker, ActionMarker, EffectMarker> {
    @Suppress("UNCHECKED_CAST")
    val source = S::class as KClass<out Store<StateMarker, ActionMarker, EffectMarker>>
    return RelayBuilder<StateMarker, ActionMarker, EffectMarker>().apply(builder).build(from = source)
}
