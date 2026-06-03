package tech.fika.monaka.binder

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.fika.monaka.core.Action
import tech.fika.monaka.core.Effect
import tech.fika.monaka.core.State
import tech.fika.monaka.core.Store

internal class DefaultBinder<SourceState : State, SourceAction : Action, SourceEffect : Effect, TargetAction : Action>(
    override val source: KClass<out Store<SourceState, SourceAction, SourceEffect>>,
    override val target: KClass<out Store<*, TargetAction, *>>,
    private val stateTransform: ((SourceState) -> TargetAction?)? = null,
    private val actionTransform: ((SourceAction) -> TargetAction?)? = null,
    private val effectTransform: ((SourceEffect) -> TargetAction?)? = null,
) : Binder<SourceState, SourceAction, SourceEffect, TargetAction> {
    @Suppress("UNCHECKED_CAST")
    override fun apply(source: Store<*, *, *>, target: Store<*, *, *>, scope: CoroutineScope): List<Job> {
        val typedSource = source as? Store<SourceState, SourceAction, SourceEffect> ?: return emptyList()
        val typedTarget = target as? Store<*, TargetAction, *> ?: return emptyList()
        val jobs = mutableListOf<Job>()
        stateTransform?.let { transform ->
            jobs += scope.launch {
                typedSource.state.collect { sourceState ->
                    transform(sourceState)?.let { action -> typedTarget.dispatch(action) }
                }
            }
        }
        effectTransform?.let { transform ->
            jobs += scope.launch {
                typedSource.effects.collect { sourceEffect ->
                    transform(sourceEffect)?.let { action -> typedTarget.dispatch(action) }
                }
            }
        }
        actionTransform?.let { transform ->
            jobs += scope.launch {
                typedSource.actions.collect { sourceAction ->
                    transform(sourceAction)?.let { action -> typedTarget.dispatch(action) }
                }
            }
        }
        return jobs
    }
}