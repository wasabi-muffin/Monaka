package dev.gmvalentino.monaka.relay

import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.runtime.StoreRegistry
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DefaultRelay<SourceState : StateMarker, SourceAction : ActionMarker, SourceEffect : EffectMarker>(
    override val source: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    private val observedTargets: MutableSet<KClass<out Store<*, *, *>>>,
    private val stateHandler: ((SourceState, StoreRegistry) -> Unit)? = null,
    private val effectHandler: ((SourceEffect, StoreRegistry) -> Unit)? = null,
    private val actionHandler: ((SourceAction, StoreRegistry) -> Unit)? = null,
) : Relay<SourceState, SourceAction, SourceEffect> {

    override val targets: Set<KClass<out Store<*, *, *>>> get() = observedTargets

    @Suppress("UNCHECKED_CAST")
    override fun apply(source: Store<*, *, *>, registry: StoreRegistry, scope: CoroutineScope): List<Job> {
        val typedSource = source as? Store<SourceState, SourceAction, SourceEffect> ?: return emptyList()
        val jobs = mutableListOf<Job>()
        stateHandler?.let { handler ->
            jobs += scope.launch {
                typedSource.state.collect { state ->
                    ifTargetsPresent(registry) { handler(state, registry) }
                }
            }
        }
        effectHandler?.let { handler ->
            jobs += scope.launch {
                typedSource.effects.collect { effect ->
                    ifTargetsPresent(registry) { handler(effect, registry) }
                }
            }
        }
        actionHandler?.let { handler ->
            jobs += scope.launch {
                typedSource.actions.collect { action ->
                    ifTargetsPresent(registry) { handler(action, registry) }
                }
            }
        }
        return jobs
    }

    /**
     * Runs [block] only when at least one declared target class has a registered instance.
     * If [targets] is empty (no dispatch has occurred yet) the block always runs, preserving
     * the original behaviour before any target class is discovered.
     */
    private fun ifTargetsPresent(
        registry: StoreRegistry,
        block: () -> Unit,
    ) {
        if (targets.isEmpty() || targets.any { it in registry }) block()
    }
}
