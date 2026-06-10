package dev.gmvalentino.monaka.examples.news.list

import dev.gmvalentino.monaka.core.SelfTransition
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition
import dev.gmvalentino.monaka.examples.news.domain.News

@SelfTransition
sealed interface NewsListState : State {
    @Transition(InitialLoading::class)
    data object Initial : NewsListState

    @Transition(Stable.Initial::class, InitialError::class)
    data object InitialLoading : NewsListState

    @SelfTransition
    sealed interface Stable : NewsListState {
        val newsList: List<News>

        @Transition(Initial::class, Loading::class)
        data class Initial(override val newsList: List<News>) : Stable

        @Transition(Initial::class, Error::class)
        data class Loading(override val newsList: List<News>) : Stable

        @Transition(Initial::class)
        data class Error(override val newsList: List<News>, val error: Throwable) : Stable
    }

    @Transition(InitialLoading::class)
    data class InitialError(val error: Throwable) : NewsListState
}
