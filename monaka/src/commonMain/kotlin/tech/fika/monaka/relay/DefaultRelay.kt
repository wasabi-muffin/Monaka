package tech.fika.monaka.relay

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.runtime.StoreRegistry

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
