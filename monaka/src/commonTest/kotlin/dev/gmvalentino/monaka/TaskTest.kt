package dev.gmvalentino.monaka

import app.cash.turbine.test
import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.dsl.store
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private sealed interface TState : State {
    data object Idle : TState
    data object Loading : TState
    data object Done : TState
    data class Result(val value: String) : TState
}

private sealed interface TAction : Action {
    data object Start : TAction
    data class Finish(val value: String) : TAction
    data object Cancel : TAction
    data object Debounce : TAction
    data object DebounceCompleted : TAction
}

private sealed interface TEffect : Effect

class TaskTest {

    @Test
    fun task_launchesAsyncWork_thenDispatches() = runTest {
        val store = store<TState, TAction, TEffect>(scope = backgroundScope) {
            initialState(TState.Idle)
            state<TState.Idle> {
                on<TAction.Start> {
                    task {
                        delay(100)
                        dispatch(TAction.Finish("hello"))
                    }
                    transition(TState.Loading)
                }
            }
            state<TState.Loading> {
                on<TAction.Finish> { transition(TState.Result(action.value)) }
            }
        }
        store.state.test {
            awaitItem() // Idle
            store.dispatch(TAction.Start)
            assertEquals(TState.Loading, awaitItem())
            delay(101) // advance virtual time past the task's delay
            assertEquals(TState.Result("hello"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun keyedTask_cancelsPreviousJobWithSameKey() = runTest {
        // Each dispatch captures a distinct isFirst flag so we can tell which task ran.
        var firstTaskRan = false
        var dispatchCount = 0
        val store = store<TState, TAction, TEffect>(scope = backgroundScope) {
            initialState(TState.Idle)
            state<TState.Idle> {
                on<TAction.Debounce> {
                    val capturedIsFirst = dispatchCount == 0
                    dispatchCount++
                    task("debounce") {
                        delay(200)
                        if (capturedIsFirst) firstTaskRan = true
                        dispatch(TAction.DebounceCompleted)
                    }
                }
                on<TAction.DebounceCompleted> {
                    transition(TState.Done)
                }
            }
        }
        store.state.test {
            awaitItem() // Idle
            store.dispatch(TAction.Debounce) // starts first job (delay 200 from T=0)
            delay(100)                        // T=100ms — first job still running
            store.dispatch(TAction.Debounce) // cancels first, starts second (delay 200 from T=100)
            delay(201)                        // T=301ms — second job completes
            assertEquals(TState.Done, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, firstTaskRan) // first task was canceled before completing
    }

    @Test
    fun cancel_stopsRunningKeyedJob() = runTest {
        var jobCompleted = false
        val store = store<TState, TAction, TEffect>(scope = backgroundScope) {
            initialState(TState.Idle)
            state<TState.Idle> {
                on<TAction.Start> {
                    task("job") {
                        delay(500)
                        jobCompleted = true
                        dispatch(TAction.Finish("done"))
                    }
                    transition(TState.Loading)
                }
            }
            state<TState.Loading> {
                on<TAction.Cancel> {
                    cancel("job")
                    transition(TState.Done)
                }
                on<TAction.Finish> { transition(TState.Result(action.value)) }
            }
        }
        store.state.test {
            awaitItem() // Idle
            store.dispatch(TAction.Start)
            assertEquals(TState.Loading, awaitItem())
            delay(100)
            store.dispatch(TAction.Cancel)
            assertEquals(TState.Done, awaitItem())
            delay(500) // advance past when job would have completed
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, jobCompleted)
    }

    @Test
    fun autoCancel_cancelsJobOnStateTypeChange() = runTest {
        var autoCanceledTaskCompleted = false
        val store = store<TState, TAction, TEffect>(scope = backgroundScope) {
            initialState(TState.Idle)
            state<TState.Idle> {
                on<TAction.Start> {
                    task(autoCancel = true) {
                        delay(500)
                        autoCanceledTaskCompleted = true
                        dispatch(TAction.Finish("done"))
                    }
                    transition(TState.Loading)
                }
            }
            state<TState.Loading> {
                on<TAction.Cancel> { transition(TState.Done) }
                on<TAction.Finish> { transition(TState.Result(action.value)) }
            }
        }
        store.state.test {
            awaitItem() // Idle
            store.dispatch(TAction.Start)
            assertEquals(TState.Loading, awaitItem()) // autoCancel task canceled on Idle→Loading
            store.dispatch(TAction.Cancel)
            assertEquals(TState.Done, awaitItem())
            delay(600)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, autoCanceledTaskCompleted)
    }

    @Test
    fun taskInActionHandler_hasAccessToTypedAction() = runTest {
        var capturedValue: String? = null
        val store = store<TState, TAction, TEffect>(scope = backgroundScope) {
            initialState(TState.Loading)
            state<TState.Loading> {
                on<TAction.Finish> {
                    task { capturedValue = action.value }
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(TAction.Finish("captured"))
            delay(1) // let task run
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("captured", capturedValue)
    }
}
