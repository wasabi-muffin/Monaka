package tech.fika.monaka.examples.counter

import tech.fika.monaka.core.Effect

sealed interface CounterEffect : Effect {
    data class ShowMessage(val text: String) : CounterEffect

    /** Instruct the consumer (e.g. ViewModel) to persist the current count. */
    data class SaveCount(val count: Int) : CounterEffect
}
