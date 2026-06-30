package dev.gmvalentino.monaka.scopes

import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.handler.HandlerResult
import dev.gmvalentino.monaka.runtime.JobRegistry
import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * Implicit receiver shared by every handler lambda (action, lifecycle, state-change, and error).
 *
 * Handlers are **statements**, not expressions: call [transition], [sideEffect], or [reject] to
 * record what the runtime should do after the lambda returns. The lambda itself returns [Unit].
 *
 * ### Result resolution
 * After the lambda finishes, the runtime resolves the recorded state as follows:
 * 1. [reject] was called → the action is treated as rejected; **all accumulated effects are
 *    discarded**; plugins notified via `onRejected`. Use [guard] instead to preserve
 *    pre-guard recordings.
 * 2. [transition] was called → the recorded state becomes the new state, followed by all
 *    accumulated [sideEffect] emissions in call order.
 * 3. Only [sideEffect] was called → effects emit; state is unchanged. This is a valid
 *    pattern for actions that should notify the UI without changing state.
 * 4. Nothing was called → silent no-op.
 *
 * ### First-write-wins for [transition]
 * The first [transition] call records the new state. Subsequent calls are silent no-ops and
 * their blocks are **not evaluated**. This enables a "fallback" pattern:
 *
 * ```kotlin
 * on<Refresh> {
 *     if (state.isStale) transition(Refreshing)
 *     transition(Active)   // fallback when not stale
 * }
 * ```
 *
 * ### Terminal [reject]
 * Once [reject] is called, all subsequent [transition], [sideEffect], [dispatch],
 * and [cancel] calls become no-ops in the same handler invocation. The runtime emits a
 * rejected result regardless of what was recorded before.
 */
@MonakaDsl
public abstract class HandlerScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    public open val machineScope: CoroutineScope,
    public open val state: SubState,
    internal val internalDispatch: (Action) -> Unit,
    internal val jobRegistry: JobRegistry,
) {

    @PublishedApi
    internal var pendingState: State? = null

    @PublishedApi
    internal val pendingEffects: MutableList<Effect> = mutableListOf()

    @PublishedApi
    internal var rejected: Boolean = false
    internal var guarded: Boolean = false

    /**
     * Enqueue [action] to be processed by this state machine.
     *
     * The action is added to the end of the queue and will be processed after the current
     * handler returns (and any other queued actions ahead of it). Safe to call from any
     * coroutine; does not suspend. No-op if [reject] has already been called.
     */
    public fun dispatch(action: Action) {
        if (guarded || rejected) return
        internalDispatch(action)
    }

    // ── Result builders ───────────────────────────────────────────────────────

    /**
     * Stop recording further results if [predicate] returns false.
     *
     * All verbs called before [guard] are preserved and returned as the handler result.
     * All verbs called after a failing [guard] — [transition], [sideEffect], [dispatch],
     * [cancel], and `task` — become no-ops for this handler invocation.
     *
     * Unlike [reject], a failing [guard] does not discard pre-guard recordings and does
     * not notify plugins via `onRejected`. The result is whatever was recorded before the
     * guard: a [transition], accumulated [sideEffect]s, or a silent no-op.
     *
     * Calling [guard] after [reject] is a no-op. Calling [reject] after a failing [guard]
     * is also a no-op — guard semantics take precedence and pre-guard effects are preserved.
     *
     * ```kotlin
     * on<MyAction.Submit> {
     *     sideEffect(MyEffect.Analytics)   // always runs
     *     guard { state.isValid }          // short-circuits below if invalid
     *     transition(MyState.Submitting)
     * }
     * ```
     */
    public fun guard(predicate: () -> Boolean) {
        if (rejected || guarded) return
        if (!predicate()) guarded = true
    }

    /**
     * Record [nextState] as the new state. First call wins — subsequent calls in the same
     * handler are no-ops.
     *
     * ```kotlin
     * on<MyAction.Load> {
     *     transition(MyState.Loading(action.id))
     * }
     * ```
     */
    public fun <S : State> transition(nextState: S) {
        if (guarded || rejected || pendingState != null) return
        pendingState = nextState
    }

    /**
     * Emit [effects] in the order they appear. Multiple calls accumulate.
     *
     * If [reject] has already been called in this handler, this call is a no-op. Conversely,
     * if [reject] is called *after* [sideEffect], the accumulated effects are discarded — no
     * effects emit when the handler is rejected. Use [guard] if you want effects recorded before
     * a failing predicate to still be emitted.
     *
     * Calling [sideEffect] without [transition] is a valid pattern — the state stays unchanged
     * and only the effects are emitted:
     *
     * ```kotlin
     * on<MyAction.Ping> {
     *     sideEffect(MyEffect.Toast("Pong"))   // state unchanged, effect emitted
     * }
     * ```
     *
     * Combined with [transition]:
     *
     * ```kotlin
     * on<MyAction.Submit> {
     *     transition(MyState.Submitting)
     *     sideEffect(MyEffect.Analytics("submit_started"))
     *     if (state.shouldNotify) sideEffect(MyEffect.Notify)
     * }
     * ```
     */
    public fun sideEffect(vararg effects: Effect) {
        if (guarded || rejected) return
        pendingEffects += effects
    }

    /**
     * Mark the action as rejected. No state change occurs, no effects emit (including any
     * [sideEffect] calls made **before** this call), and plugins are notified via
     * [dev.gmvalentino.monaka.plugin.Plugin.onRejected].
     *
     * Terminal: all subsequent [transition], [sideEffect], [dispatch], [task], and [cancel]
     * calls in the same handler become no-ops.
     *
     * If you want verbs recorded before a failing predicate to still take effect (e.g. emit an
     * analytics effect even when the transition is skipped), use [guard] instead — a failing
     * [guard] preserves pre-guard recordings and does not notify plugins.
     *
     * ```kotlin
     * on<MyAction.Submit> {
     *     if (!state.isValid) { reject(); return@on }
     *     transition(MyState.Submitting)
     * }
     * ```
     */
    public fun reject() {
        if (guarded) return
        rejected = true
    }

    /**
     * Cancel the job registered under [key], if any, and remove it from the registry.
     * No-op if [reject] or [guard] has already been called.
     */
    public fun cancel(key: String) {
        if (guarded || rejected) return
        jobRegistry.cancel(key)
    }

    /**
     * Internal: snapshot the accumulated [HandlerScope] mutations into a [HandlerResult].
     *
     * Called by the runtime after each handler lambda returns. Order of precedence:
     * 1. If [HandlerScope.rejected] was set → [HandlerResult.Rejected].
     * 2. Else if [HandlerScope.pendingState] was set → [HandlerResult.Transition] carrying
     *    the new state and all accumulated [HandlerScope.pendingEffects] in call order.
     * 3. Else if any side effects were emitted → [HandlerResult.SideEffect].
     * 4. Otherwise → [HandlerResult.Done].
     */
    internal fun consumeResult(): HandlerResult<State, Effect> = when {
        rejected -> HandlerResult.Rejected
        pendingState != null -> HandlerResult.Transition(state = pendingState!!, effects = pendingEffects.toList())
        pendingEffects.isNotEmpty() -> HandlerResult.SideEffect(effects = pendingEffects.toList())
        else -> HandlerResult.Done
    }
}
