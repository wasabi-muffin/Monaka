package dev.gmvalentino.monaka.examples.news.details

import dev.gmvalentino.monaka.core.Action

sealed interface NewsDetailsAction : Action {
    data object ClickBack : NewsDetailsAction
    sealed interface Error : NewsDetailsAction {
        data object ClickRetry : Error
    }
}
