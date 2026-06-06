package tech.fika.monaka.examples.livefeed

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.plugin.LoggingPlugin

/**
 * Live-feed machine demonstrating keyed job patterns with automatic state-scoped
 * cancellation.
 *
 * ### Task lifecycle
 *
 * All keyed tasks (`task(key) { }`) must be explicitly cancelled via `cancel(key)`,
 * unless they were started with `autoCancel = true` (cancelled by the runtime on the
 * next state-type change). Use `onExit { cancel("key") }` to clean up on state exit,
 * or call `cancel(key)` directly in the action handler that triggers the transition
 * (e.g. cancelling "poll" before moving to `Failed`).
 *
 * ---
 *
 * ### Pattern 1 — Debounce (keyed, cancels previous on each keystroke)
 *
 * `task("search")` cancels the previous search job automatically (key-based),
 * so every keystroke replaces the in-flight search.
 *
 * ```
 * QueryChanged
 *   │ task("search") {            ← cancels previous "search" job immediately
 *   │     delay(300)
 *   │     dispatch(SearchCompleted(...) or SearchFailed(...))
 *   │ }
 *   └─▶ state.copy(isLoading = true)
 * ```
 *
 * ---
 *
 * ### Pattern 2 — Long-running loop (keyed start/stop within a state)
 *
 * [FeedAction.GoLive] uses `task("poll")` to start a polling loop.
 * [FeedAction.PauseLive] calls `cancel("poll")` to stop it — both happen inside
 * `Active`, so explicit cancel is needed here. If the machine leaves `Active`
 * entirely (e.g. `SearchFailed` → `Failed`), the poll is cancelled automatically.
 *
 * ```
 * GoLive  →  task("poll") { while (true) { delay(5s); dispatch(NewItems(...)) } }
 * PauseLive  →  cancel("poll")          ← explicit; still in Active
 * SearchFailed → cancel("poll")         ← explicit; before transitioning to Failed
 * ```
 *
 * ---
 *
 * ### Pattern 3 — Fire-and-forget (unkeyed, machine lifetime)
 *
 * Analytics events use the unkeyed `task { }`. These are not state-scoped and
 * are not tracked in the registry — they run to completion regardless of state changes.
 *
 * ---
 *
 * ### Pattern 4 — Exponential back-off retry (keyed, replaces previous attempt)
 *
 * `task("search")` in `Failed` replaces any previous attempt. A generation counter
 * threads through the dispatched action so a stale result is discarded if the query
 * has since changed.
 */
class FeedStateMachine(
    feedRepository: FeedRepository,
    analyticsRepository: AnalyticsRepository,
) : StateMachine<FeedState, FeedAction, FeedEffect> by stateMachine(builder = {

    initialState(FeedState.Idle)

    // ── Idle ──────────────────────────────────────────────────────────────

    state<FeedState.Idle> {
        on<FeedAction.QueryChanged> {
            if (action.query.isBlank()) return@on

            // Pattern 1 — start first debounce timer
            task("search") {
                delay(DEBOUNCE_MS)
                val result = runCatching { feedRepository.search(action.query) }
                if (result.isSuccess) {
                    dispatch(FeedAction.SearchCompleted(action.query, result.getOrThrow()))
                } else {
                    dispatch(FeedAction.SearchFailed(action.query, result.exceptionOrNull()?.message ?: "Unknown error"))
                }
            }

            transition(state.toActive(query = action.query, items = emptyList(), isLoading = true, isLive = false))
        }
    }

    // ── Active ────────────────────────────────────────────────────────────

    state<FeedState.Active> {

        // Pattern 1 — debounced search on every keystroke
        on<FeedAction.QueryChanged> {
            if (action.query.isBlank()) {
                cancel("poll")
                cancel("search")
                return@on transition(state.toIdle())
            }

            // task("search") auto-cancels the previous debounce job (key-based)
            task("search") {
                delay(DEBOUNCE_MS)
                runCatching { feedRepository.search(action.query) }.onSuccess { result ->
                    dispatch(FeedAction.SearchCompleted(action.query, result))
                    launch { runCatching { analyticsRepository.trackSearch(action.query, result.size) } }
                }.onFailure { error ->
                    dispatch(FeedAction.SearchFailed(action.query, error.message ?: "Unknown error"))
                }
            }

            // Query changed while live — timestamps are no longer valid; stop poll explicitly
            // (still in Active, so auto-cancel won't fire here)
            if (state.isLive) cancel("poll")
            transition(state.copy(query = action.query, isLoading = true, isLive = false))
        }

        on<FeedAction.Refresh> {
            task("search") {
                val result = runCatching { feedRepository.search(state.query) }
                if (result.isSuccess) {
                    dispatch(FeedAction.SearchCompleted(state.query, result.getOrThrow()))
                } else {
                    dispatch(FeedAction.SearchFailed(state.query, result.exceptionOrNull()?.message ?: "Unknown error"))
                }
            }
            transition(state.copy(isLoading = true))
        }

        on<FeedAction.SearchCompleted> {
            if (action.query != state.query) return@on
            transition(state.copy(items = action.items, isLoading = false))
        }

        on<FeedAction.SearchFailed> {
            if (action.query != state.query) return@on
            cancel("poll")
            transition(state.toFailed(query = action.query, message = action.message, retryCount = 0))
            sideEffect(FeedEffect.ShowToast("Search failed — tap Retry"))
        }

        // Pattern 2 — start live polling loop
        on<FeedAction.GoLive> {
            if (state.isLive) return@on
            val sinceTimestamp = state.items.maxOfOrNull { it.timestamp } ?: 0L

            task("poll") {
                var since = sinceTimestamp
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    runCatching { feedRepository.fetchSince(state.query, since) }
                        .onSuccess { newItems ->
                            if (newItems.isNotEmpty()) {
                                since = newItems.maxOf { it.timestamp }
                                dispatch(FeedAction.NewItems(newItems))
                            }
                        }
                    // Polling failures are silent — the loop retries next cycle
                }
            }

            transition(state.copy(isLive = true))
        }

        // Pattern 2 — cancel the polling loop (still in Active; explicit cancel required)
        on<FeedAction.PauseLive> {
            cancel("poll")
            transition(state.copy(isLive = false))
        }

        on<FeedAction.NewItems> {
            val merged = (action.items + state.items)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
                .take(MAX_ITEMS)
            transition(state.copy(items = merged))
        }

        // Pattern 3 — fire-and-forget analytics; state unchanged
        on<FeedAction.ItemViewed> {
            task { runCatching { analyticsRepository.trackItemViewed(action.itemId) } }
        }
    }

    // ── Failed ────────────────────────────────────────────────────────────

    state<FeedState.Failed> {

        // Pattern 4 — exponential back-off retry
        // Note: "poll" is explicitly cancelled in the SearchFailed handler before this state is entered.
        on<FeedAction.Retry> {
            val backoffMs = minOf(BASE_BACKOFF_MS shl state.retryCount, MAX_BACKOFF_MS)
            val nextGeneration = state.retryCount + 1

            task("search") {
                delay(backoffMs)
                val result = runCatching { feedRepository.search(state.query) }
                if (result.isSuccess) {
                    dispatch(FeedAction.SearchCompleted(state.query, result.getOrThrow()))
                } else {
                    dispatch(FeedAction.SearchFailed(state.query, result.exceptionOrNull()?.message ?: "Unknown error", nextGeneration))
                }
            }
            transition(state.toActive(query = state.query, items = emptyList(), isLoading = true, isLive = false))
        }

        on<FeedAction.SearchCompleted> {
            transition(state.toActive(query = action.query, items = action.items, isLoading = false, isLive = false))
        }

        on<FeedAction.SearchFailed> {
            transition(state.copy(message = action.message, retryCount = action.generation))
            sideEffect(FeedEffect.ShowToast("Retry ${action.generation} failed — tap to try again"))
        }

        on<FeedAction.QueryChanged> {
            if (action.query.isBlank()) {
                cancel("search")
                return@on transition(state.toIdle())
            }
            task("search") {
                delay(DEBOUNCE_MS)
                val result = runCatching { feedRepository.search(action.query) }
                if (result.isSuccess) {
                    dispatch(FeedAction.SearchCompleted(action.query, result.getOrThrow()))
                } else {
                    dispatch(FeedAction.SearchFailed(action.query, result.exceptionOrNull()?.message ?: "Unknown error"))
                }
            }
            transition(state.toActive(query = action.query, items = emptyList(), isLoading = true, isLive = false))
        }
    }

    // ── Parent state — matches any FeedState ─────────────────────────────

    // Clear is valid from any state. Registered on the parent sealed interface
    // so it fires regardless of which substate the machine is currently in.
    // Explicit cancel ensures jobs stop immediately rather than waiting for the
    // next action in Idle to trigger auto-cancellation.
    state<FeedState> {
        on<FeedAction.Clear> {
            cancel("search")
            cancel("poll")
            transition(FeedState.Idle)
        }
    }

    install(LoggingPlugin(tag = "Feed"))
})

private const val DEBOUNCE_MS = 300L
private const val POLL_INTERVAL_MS = 5_000L
private const val BASE_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val MAX_ITEMS = 200
