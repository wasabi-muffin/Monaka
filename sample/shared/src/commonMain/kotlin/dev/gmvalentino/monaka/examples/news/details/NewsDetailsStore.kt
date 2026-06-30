package dev.gmvalentino.monaka.examples.news.details

import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import kotlinx.coroutines.CoroutineScope

class NewsDetailsStore(
    stateMachine: NewsDetailsStateMachine,
    scope: CoroutineScope,
    initialState: NewsDetailsState,
) : Store<NewsDetailsState, NewsDetailsAction, NewsDetailsEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
