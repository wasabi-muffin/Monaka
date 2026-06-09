package dev.gmvalentino.monaka.examples.counter

import dev.gmvalentino.monaka.core.State

data class CounterState(
    val count: Int,
    val step: Int = 1,
) : State
