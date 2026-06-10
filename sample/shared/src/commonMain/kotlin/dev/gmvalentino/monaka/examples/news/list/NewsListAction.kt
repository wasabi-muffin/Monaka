package dev.gmvalentino.monaka.examples.news.list

import dev.gmvalentino.monaka.core.Action

sealed interface NewsListAction : Action {
    data object LoadInitial : NewsListAction
    sealed interface Error : NewsListAction {
        data object ClickRetry : Error
        data object ClickOk : Error
    }
    data class ClickNews(val id: Int) : NewsListAction
    data object ClickReadAll : NewsListAction
    data class OnUpdateRead(val id: Int) : NewsListAction
}
