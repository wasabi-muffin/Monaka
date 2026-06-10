package dev.gmvalentino.monaka.examples.news.details

import dev.gmvalentino.monaka.core.Effect

sealed interface NewsDetailsEffect : Effect {
    data object NavigateBack : NewsDetailsEffect
}
