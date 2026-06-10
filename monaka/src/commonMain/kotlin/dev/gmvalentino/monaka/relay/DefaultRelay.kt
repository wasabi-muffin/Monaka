package dev.gmvalentino.monaka.relay

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.runtime.StoreRegistry

internal class DefaultRelay<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker>(
    override val source: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    private val stateHandler: ((SourceState, StoreRegistry) -> Unit)? = null,
    private val effectHandler: ((SourceEffect, StoreRegistry) -> Unit)? = null,
    private val actionHandler: ((SourceAction, StoreRegistry) -> Unit)? = null,
) : Relay<SourceState, SourceAction, SourceEffect> {

    @Suppress("UNCHECKED_CAST")
    override fun apply(source: Store<*, *, *>, registry: StoreRegistry, scope: CoroutineScope): List<Job> {
        val typedSource = source as? Store<SourceState, SourceAction, SourceEffect> ?: return emptyList()
        val jobs = mutableListOf<Job>()
        stateHandler?.let { handler ->
            jobs += scope.launch {
                typedSource.state.collect { state -> handler(state, registry) }
            }
        }
        effectHandler?.let { handler ->
            jobs += scope.launch {
                typedSource.effects.collect { effect -> handler(effect, registry) }
            }
        }
        actionHandler?.let { handler ->
            jobs += scope.launch {
                typedSource.actions.collect { action -> handler(action, registry) }
            }
        }
        return jobs
    }
}
