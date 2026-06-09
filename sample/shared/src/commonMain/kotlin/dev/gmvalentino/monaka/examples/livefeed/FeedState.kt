package dev.gmvalentino.monaka.examples.livefeed

import dev.gmvalentino.monaka.core.SelfTransition
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition

@SelfTransition
sealed interface FeedState : State {

    /** Nothing has been searched yet. */
    @Transition(Active::class)
    data object Idle : FeedState

    /**
     * A query is active. Covers loading, showing results, and live-polling
     * in a single state — so the query is preserved across all sub-phases.
     */
    @Transition(Failed::class, Idle::class)
    data class Active(
        val query: String,
        val items: List<FeedItem> = emptyList(),
        val isLoading: Boolean = true,
        val isLive: Boolean = false,
    ) : FeedState

    /** The most recent search attempt failed. Holds retry metadata. */
    @Transition(Active::class, Idle::class)
    data class Failed(
        val query: String,
        val message: String,
        val retryCount: Int = 0,
    ) : FeedState
}
