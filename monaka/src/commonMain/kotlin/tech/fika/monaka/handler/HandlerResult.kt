package tech.fika.monaka.handler

import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker

/**
 * The result returned by every handler lambda — action handlers, lifecycle hooks,
 * and state-change hooks.
 *
 * Four subtypes cover all outcomes:
 *
 * - [Transition]  — move to a new state, optionally emitting [Transition.effects].
 * - [SideEffect]  — state unchanged, emit [SideEffect.effects].
 * - [Rejected]    — guard condition failed; state unchanged, plugins notified via
 *                   [tech.fika.monaka.plugin.Plugin.onInvalid].
 * - [Done]        — terminal no-op; state unchanged, no effects, no plugin notification.
 *                   Returned by [tech.fika.monaka.dsl.HandlerScope.dispatch],
 *                   [tech.fika.monaka.dsl.HandlerScope.task], and
 *                   [tech.fika.monaka.dsl.HandlerScope.cancel] so that
 *                   `return@on dispatch(action)` compiles cleanly.
 *
 * All subtypes are covariant in both [State] and [Effect] so that, for example,
 * a `HandlerResult.Transition<SubState, SpecificEffect>` is assignable where a
 * `HandlerResult<ParentState, ParentEffect>` is expected.
 */
sealed interface HandlerResult<out State : StateMarker, out Effect : EffectMarker> {

    /**
     * A successful transition: move to [state] and emit [effects].
     */
    data class Transition<out State : StateMarker, out Effect : EffectMarker>(
        val state: State,
        val effects: List<Effect> = emptyList(),
    ) : HandlerResult<State, Effect>

    /**
     * State stays unchanged, but [effects] are emitted.
     *
     * Use this when the action is valid and the machine should stay in the current state,
     * but still needs to notify the UI (e.g. show a toast, trigger navigation).
     */
    data class SideEffect<out Effect : EffectMarker>(
        val effects: List<Effect>,
    ) : HandlerResult<Nothing, Effect>

    /**
     * A guard condition failed — state unchanged, no effects emitted.
     *
     * Notifies plugins via [tech.fika.monaka.plugin.Plugin.onInvalid].
     * Use when the action is not applicable in the current state.
     */
    data object Rejected : HandlerResult<Nothing, Nothing>

    /**
     * Terminal no-op — state unchanged, no effects emitted, plugins not notified.
     */
    data object Done : HandlerResult<Nothing, Nothing>
}
