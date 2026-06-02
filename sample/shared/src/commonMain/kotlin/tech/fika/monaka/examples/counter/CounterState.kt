package tech.fika.monaka.examples.counter

import tech.fika.monaka.core.State

data class CounterState(
    val count: Int,
    val step: Int = 1,
) : State
