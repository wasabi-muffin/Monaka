package dev.gmvalentino.monaka.relay

import dev.gmvalentino.monaka.core.Store
import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * Create a [Relay] that observes stores of class [from].
 *
 * ### Example
 * ```kotlin
 * val authRelay = relay(from = AuthStore::class) {
 *     state<AuthState.SignedIn>  { dispatch(CartStore::class, CartAction.LoadForUser(event.user.id)) }
 *     state<AuthState.SignedOut> {
 *         dispatch(CartStore::class, CartAction.Clear)
 *         dispatch(CheckoutStore::class, CheckoutAction.Cancel)
 *     }
 * }
 *
 * registry.bind(authRelay)
 * ```
 *
 * @param from    KClass of the source store type.
 * @param builder DSL block to configure state, effect, and action relays.
 */
public fun <SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker> relay(
    from: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    builder: RelayBuilder<SourceState, SourceAction, SourceEffect>.() -> Unit,
): Relay<SourceState, SourceAction, SourceEffect> = RelayBuilder<SourceState, SourceAction, SourceEffect>().apply(builder).build(from = from)
