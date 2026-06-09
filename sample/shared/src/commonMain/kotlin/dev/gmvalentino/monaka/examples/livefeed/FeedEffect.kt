package dev.gmvalentino.monaka.examples.livefeed

import dev.gmvalentino.monaka.core.Effect

sealed interface FeedEffect : Effect {
    data class ShowToast(val message: String) : FeedEffect
}
