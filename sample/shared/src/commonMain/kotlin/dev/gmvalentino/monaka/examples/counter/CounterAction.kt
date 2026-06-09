package dev.gmvalentino.monaka.examples.counter

import dev.gmvalentino.monaka.core.Action

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data class SetStep(val step: Int) : CounterAction
    data object Reset : CounterAction

    /** Dispatched by the consumer after completing an async save. */
    data class SaveCompleted(val success: Boolean) : CounterAction
}
