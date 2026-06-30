package dev.gmvalentino.monaka.examples.news.details

import dev.gmvalentino.monaka.dsl.StateMachine
import dev.gmvalentino.monaka.dsl.stateMachine
import dev.gmvalentino.monaka.examples.news.domain.GetNewsDetailsUseCase

class NewsDetailsStateMachine(
    getNewsDetailsUseCase: GetNewsDetailsUseCase,
) : StateMachine<NewsDetailsState, NewsDetailsAction, NewsDetailsEffect> by stateMachine(
    builder = {
        state<NewsDetailsState> {
            on<NewsDetailsAction.ClickBack> {
                sideEffect(NewsDetailsEffect.NavigateBack)
            }
        }

        state<NewsDetailsState.Initial> {
            onEnter {
                transition(state.toInitialLoading())
            }
        }

        state<NewsDetailsState.InitialLoading> {
            onEnter {
                runCatching { getNewsDetailsUseCase(id = state.id) }.fold(
                    onSuccess = { transition(state.toStable(newsDetails = it)) },
                    onFailure = { transition(state.toError(error = it)) },
                )
            }
        }

        state<NewsDetailsState.Error> {
            on<NewsDetailsAction.Error.ClickRetry> {
                transition(state.toInitialLoading())
            }
        }

        state<NewsDetailsState.Stable> {
        }
    }
)
