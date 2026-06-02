package tech.fika.monaka.examples.livefeed

import tech.fika.monaka.core.State

sealed interface FeedState : State {

    /** Nothing has been searched yet. */
    data object Idle : FeedState

    /**
     * A query is active. Covers loading, showing results, and live-polling
     * in a single state — so the query is preserved across all sub-phases.
     */
    data class Active(
        val query: String,
        val items: List<FeedItem> = emptyList(),
        val isLoading: Boolean = true,
        val isLive: Boolean = false,
    ) : FeedState

    /** The most recent search attempt failed. Holds retry metadata. */
    data class Failed(
        val query: String,
        val message: String,
        val retryCount: Int = 0,
    ) : FeedState
}
