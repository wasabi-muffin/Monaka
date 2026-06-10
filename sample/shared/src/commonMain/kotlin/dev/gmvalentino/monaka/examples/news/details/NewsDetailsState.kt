package dev.gmvalentino.monaka.examples.news.details

import dev.gmvalentino.monaka.core.SelfTransition
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition
import dev.gmvalentino.monaka.examples.news.domain.NewsDetails

@SelfTransition
sealed interface NewsDetailsState : State {
    val id: Int

    @Transition(InitialLoading::class)
    data class Initial(override val id: Int) : NewsDetailsState

    @Transition(Stable::class, Error::class)
    data class InitialLoading(override val id: Int) : NewsDetailsState

    data class Stable(override val id: Int, val newsDetails: NewsDetails) : NewsDetailsState

    @Transition(InitialLoading::class)
    data class Error(override val id: Int, val error: Throwable) : NewsDetailsState
}
