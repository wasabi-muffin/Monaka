package dev.gmvalentino.monaka.examples.counter

import dev.gmvalentino.monaka.core.Effect

sealed interface CounterEffect : Effect {
    data class ShowMessage(val text: String) : CounterEffect

    /** Instruct the consumer (e.g. ViewModel) to persist the current count. */
    data class SaveCount(val count: Int) : CounterEffect
}
