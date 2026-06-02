package tech.fika.monaka.examples.livefeed

import tech.fika.monaka.core.Effect

sealed interface FeedEffect : Effect {
    data class ShowToast(val message: String) : FeedEffect
}
