package dev.gmvalentino.monaka.examples.news.details

import dev.gmvalentino.monaka.examples.news.list.NewsListAction
import dev.gmvalentino.monaka.examples.news.list.NewsListStore
import dev.gmvalentino.monaka.relay.Relay
import dev.gmvalentino.monaka.relay.relay

object NewsDetailsRelay : Relay<NewsDetailsState, NewsDetailsAction, NewsDetailsEffect> by relay(
    from = NewsDetailsStore::class,
    builder = {
        state<NewsDetailsState.Stable> {
            dispatch(NewsListStore::class, NewsListAction.OnUpdateRead(id = event.id))
        }
    },
)
