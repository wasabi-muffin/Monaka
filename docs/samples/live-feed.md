# Live feed

**Source:** `sample/shared/…/examples/livefeed/`

The live-feed example is a search-and-stream feature demonstrating four async task patterns in a
single machine: debounce, long-running polling, fire-and-forget analytics, and exponential
back-off retry. It also shows how explicit `cancel()` and `autoCancel` complement each other for
clean job lifecycle management.

---

## States

```
Idle     — no query; waiting for the user to type
Active   — search results visible; optionally streaming live updates
Failed   — last search failed; showing retry UI
```

---

## Four task patterns

### Pattern 1 — Debounce (keyed, replaces on each keystroke)

Every `QueryChanged` action launches `task("search")`. Because the key is the same, each new
keystroke **cancels the previous in-flight task** before starting a new one — no separate debounce
timer needed:

```kotlin
on<FeedAction.QueryChanged> {
    task("search") {
        delay(300)   // virtual time in tests; real time in production
        runCatching { feedRepository.search(action.query) }
            .onSuccess { dispatch(FeedAction.SearchCompleted(action.query, it)) }
            .onFailure { dispatch(FeedAction.SearchFailed(action.query, it.message ?: "…")) }
    }
    transition(state.copy(query = action.query, isLoading = true, isLive = false))
}
```

The handler transitions immediately (non-blocking) while the debounced search runs in parallel.

### Pattern 2 — Long-running polling loop (keyed, explicit start/stop)

`GoLive` starts a polling coroutine that runs indefinitely, fetching new items every 5 seconds.
`PauseLive` stops it explicitly. Both actions are handled inside `Active`, so the state type
doesn't change — `autoCancel` wouldn't help here. Explicit `cancel("poll")` is required:

```kotlin
on<FeedAction.GoLive> {
    task("poll") {
        while (true) {
            delay(5_000)
            runCatching { feedRepository.fetchSince(state.query, sinceTimestamp) }
                .onSuccess { if (it.isNotEmpty()) dispatch(FeedAction.NewItems(it)) }
        }
    }
    transition(state.copy(isLive = true))
}

on<FeedAction.PauseLive> {
    cancel("poll")
    transition(state.copy(isLive = false))
}
```

When the machine leaves `Active` entirely (e.g. to `Failed`), the `SearchFailed` handler
explicitly calls `cancel("poll")` before transitioning — there is no `onExit` for this, since
the polling is intentionally state-scoped to `Active`.

### Pattern 3 — Fire-and-forget analytics (unkeyed, machine lifetime)

Analytics tracking uses an unkeyed `task { }`. Unkeyed tasks are not stored in the registry —
they run until they finish regardless of state changes, and cannot be cancelled by the machine:

```kotlin
on<FeedAction.ItemViewed> {
    task { runCatching { analyticsRepository.trackItemViewed(action.itemId) } }
    // no transition — state is unchanged
}
```

Use unkeyed tasks for work you never need to cancel: analytics, logging, one-shot
fire-and-forget RPCs.

### Pattern 4 — Exponential back-off retry (keyed, replaces previous attempt)

When a search fails, the machine enters `Failed` and offers a `Retry` action. Each retry doubles
the backoff delay (capped at 30 seconds), and `task("search")` ensures that a late response
from a previous attempt is discarded if a new retry has started:

```kotlin
state<FeedState.Failed> {
    on<FeedAction.Retry> {
        val backoffMs = minOf(BASE_BACKOFF_MS shl state.retryCount, MAX_BACKOFF_MS)
        val nextGeneration = state.retryCount + 1

        task("search") {
            delay(backoffMs)
            runCatching { feedRepository.search(state.query) }
                .onSuccess { dispatch(FeedAction.SearchCompleted(state.query, it)) }
                .onFailure { dispatch(FeedAction.SearchFailed(state.query, it.message ?: "…", nextGeneration)) }
        }
        transition(state.toActive(query = state.query, items = emptyList(), isLoading = true, isLive = false))
    }

    // A late SearchCompleted from a previous attempt — accept it if it's still current
    on<FeedAction.SearchCompleted> {
        transition(state.toActive(query = action.query, items = action.items, isLoading = false, isLive = false))
    }

    // Increment retry count; SearchFailed carries the generation number
    on<FeedAction.SearchFailed> {
        transition(state.copy(message = action.message, retryCount = action.generation))
        sideEffect(FeedEffect.ShowToast("Retry ${action.generation} failed — tap to try again"))
    }
}
```

---

## Stale result guard

When a search result arrives (`SearchCompleted` or `SearchFailed`), the handler checks that the
query in the action still matches the current state's query. If the user typed something new
while the search was in-flight, the result is silently discarded:

```kotlin
on<FeedAction.SearchCompleted> {
    if (action.query != state.query) return@on   // stale — discard
    transition(state.copy(items = action.items, isLoading = false))
}
```

This is a common pattern for debounced async work where results may arrive out of order.

---

## Parent catch-all for `Clear`

`Clear` is valid from any state. Rather than duplicating the handler in `Idle`, `Active`, and
`Failed`, it is registered on the parent `state<FeedState>` block. The explicit `cancel` calls
ensure any in-flight jobs are stopped immediately before transitioning to `Idle`:

```kotlin
state<FeedState> {
    on<FeedAction.Clear> {
        cancel("search")
        cancel("poll")
        transition(FeedState.Idle)
    }
}
```
