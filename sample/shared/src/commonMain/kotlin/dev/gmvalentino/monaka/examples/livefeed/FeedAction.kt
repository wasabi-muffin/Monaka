package dev.gmvalentino.monaka.examples.livefeed

import dev.gmvalentino.monaka.core.Action

sealed interface FeedAction : Action {
    data class QueryChanged(val query: String) : FeedAction

    // ── Internal — dispatched from background coroutines ──────────────────────
    /** Carries the query so stale results from a canceled job can be discarded. */
    data class SearchCompleted(val query: String, val items: List<FeedItem>) : FeedAction

    /** Carries a [generation] counter so old retry results are discarded. */
    data class SearchFailed(val query: String, val message: String, val generation: Int = 0) : FeedAction
    data class NewItems(val items: List<FeedItem>) : FeedAction

    // ── User-initiated ────────────────────────────────────────────────────────
    data object Refresh : FeedAction
    data object GoLive : FeedAction
    data object PauseLive : FeedAction
    data class ItemViewed(val itemId: String) : FeedAction
    data object Retry : FeedAction
    data object Clear : FeedAction
}
