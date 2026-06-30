package dev.gmvalentino.monaka.examples.news.list

import dev.gmvalentino.monaka.dsl.StateMachine
import dev.gmvalentino.monaka.dsl.stateMachine
import dev.gmvalentino.monaka.examples.news.domain.GetNewsListUseCase
import dev.gmvalentino.monaka.examples.news.domain.MarkAllNewsAsReadUseCase

class NewsListStateMachine(
    getNewsListUseCase: GetNewsListUseCase,
    markAllNewsAsReadUseCase: MarkAllNewsAsReadUseCase,
) : StateMachine<NewsListState, NewsListAction, NewsListEffect> by stateMachine(
    builder = {
        initialState(NewsListState.Initial)

        state<NewsListState.Initial> {
            on<NewsListAction.LoadInitial> {
                transition(state.toInitialLoading())
            }
        }

        state<NewsListState.InitialLoading> {
            onEnter {
                runCatching { getNewsListUseCase() }.fold(
                    onSuccess = { transition(state.toStableInitial(newsList = it)) },
                    onFailure = { transition(state.toInitialError(error = it)) },
                )
            }
        }

        state<NewsListState.InitialError> {
            on<NewsListAction.Error.ClickRetry> {
                transition(state.toInitialLoading())
            }
        }

        state<NewsListState.Stable> {
            on<NewsListAction.OnUpdateRead> {
                transition(state.toSelf(newsList = state.newsList.map { if (it.id == action.id) it.copy(isRead = true) else it }))
            }
        }

        state<NewsListState.Stable.Initial> {
            on<NewsListAction.ClickNews> {
                sideEffect(NewsListEffect.NavigateNewsDetails(id = action.id))
            }
            on<NewsListAction.ClickReadAll> {
                transition(state.toLoading())
            }
        }

        state<NewsListState.Stable.Loading> {
            onEnter {
                runCatching { markAllNewsAsReadUseCase() }.fold(
                    onSuccess = { transition(state.toInitial(newsList = state.newsList.map { it.copy(isRead = true) })) },
                    onFailure = { transition(state.toError(error = it)) },
                )
            }
        }

        state<NewsListState.Stable.Error> {
            on<NewsListAction.Error.ClickOk> {
                transition(state.toInitial())
            }
        }
    },
)
