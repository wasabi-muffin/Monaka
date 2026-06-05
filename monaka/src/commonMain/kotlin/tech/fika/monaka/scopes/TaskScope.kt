package tech.fika.monaka.scopes

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.dsl.MonakaDsl

/**
 * Implicit receiver inside every `task { }` and `task("key") { }` lambda.
 *
 * Exposes [state] and [dispatch] — the only verbs that are valid inside an async task body.
 * All [CoroutineScope] APIs (`delay`, `launch`, `withContext`, …) are also available via
 * delegation.
 *
 * @see ActionTaskScope for the subtype used inside `on<>` handlers, which additionally
 *      exposes a typed [ActionTaskScope.action] property.
 */
@MonakaDsl
open class TaskScope<State : StateMarker, Action : ActionMarker, SubState : State> internal constructor(
    scope: CoroutineScope,
    open val state: SubState,
    private val internalDispatch: (Action) -> Unit,
) : CoroutineScope by scope {

    /**
     * Enqueue [action] to be processed by the machine.
     *
     * Unlike [HandlerScope.dispatch], this variant carries no guard checks: tasks run
     * asynchronously after the handler has already returned, so the originating handler's
     * `guarded` / `rejected` state is no longer relevant.
     */
    fun dispatch(action: Action) = internalDispatch(action)
}

/**
 * Task scope for lambdas launched from inside `on<ActionType>` handlers.
 *
 * Extends [TaskScope] with a typed [action] property so that the dispatched action
 * remains accessible inside the coroutine body without manual capture.
 *
 * ```kotlin
 * on<FeedAction.QueryChanged> {
 *     task("search") {
 *         val results = repository.search(action.query)   // action: FeedAction.QueryChanged
 *         dispatch(FeedAction.SearchCompleted(results))
 *     }
 *     transition { state.copy(isLoading = true) }
 * }
 * ```
 */
@MonakaDsl
class ActionTaskScope<State : StateMarker, Action : ActionMarker, SubState : State, ActionType : Action> internal constructor(
    scope: CoroutineScope,
    state: SubState,
    val action: ActionType,
    internalDispatch: (Action) -> Unit,
) : TaskScope<State, Action, SubState>(scope, state, internalDispatch)
