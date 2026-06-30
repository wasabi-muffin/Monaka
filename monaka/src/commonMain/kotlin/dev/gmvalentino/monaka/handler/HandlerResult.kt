package dev.gmvalentino.monaka.handler

import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * The result returned by every handler lambda — action handlers, lifecycle hooks,
 * and state-change hooks.
 *
 * Four subtypes cover all outcomes:
 *
 * - [Transition]  — move to a new state, optionally emitting [Transition.effects].
 * - [SideEffect]  — state unchanged, emit [SideEffect.effects].
 * - [Rejected]    — guard condition failed; state unchanged, plugins notified via
 *                   [dev.gmvalentino.monaka.plugin.Plugin.onRejected].
 * - [Done]        — terminal no-op; state unchanged, no effects, no plugin notification.
 *                   Returned by [dev.gmvalentino.monaka.dsl.HandlerScope.dispatch],
 *                   [dev.gmvalentino.monaka.dsl.HandlerScope.task], and
 *                   [dev.gmvalentino.monaka.dsl.HandlerScope.cancel] so that
 *                   `return@on dispatch(action)` compiles cleanly.
 *
 * All subtypes are covariant in both [State] and [Effect] so that, for example,
 * a `HandlerResult.Transition<SubState, SpecificEffect>` is assignable where a
 * `HandlerResult<ParentState, ParentEffect>` is expected.
 */
internal sealed interface HandlerResult<out State : StateMarker, out Effect : EffectMarker> {

    /**
     * A successful transition: move to [state] and emit [effects].
     */
    public data class Transition<out State : StateMarker, out Effect : EffectMarker>(
        public val state: State,
        public val effects: List<Effect> = emptyList(),
    ) : HandlerResult<State, Effect>

    /**
     * State stays unchanged, but [effects] are emitted.
     *
     * Use this when the action is valid and the machine should stay in the current state,
     * but still needs to notify the UI (e.g. show a toast, trigger navigation).
     *
     * Produced automatically when only [dev.gmvalentino.monaka.scopes.HandlerScope.sideEffect]
     * is called in a handler (no [dev.gmvalentino.monaka.scopes.HandlerScope.transition]):
     *
     * ```kotlin
     * on<MyAction.Ping> {
     *     sideEffect(MyEffect.Toast("Pong"))   // HandlerResult.SideEffect — state unchanged
     * }
     * ```
     */
    public data class SideEffect<out Effect : EffectMarker>(
        public val effects: List<Effect>,
    ) : HandlerResult<Nothing, Effect>

    /**
     * A guard condition failed — state unchanged, no effects emitted.
     *
     * Notifies plugins via [dev.gmvalentino.monaka.plugin.Plugin.onRejected].
     * Use when the action is not applicable in the current state.
     */
    public data object Rejected : HandlerResult<Nothing, Nothing>

    /**
     * Terminal no-op — state unchanged, no effects emitted, plugins not notified.
     */
    public data object Done : HandlerResult<Nothing, Nothing>
}
