package tech.fika.monaka.ext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.store
import tech.fika.monaka.plugin.Plugin
import tech.fika.monaka.runtime.StoreRegistry
import tech.fika.monaka.runtime.registerScoped

fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> ViewModel.createStore(
    registry: StoreRegistry,
    stateMachine: StateMachine<State, Action, Effect>,
    initialState: State,
    vararg plugins: Plugin<State, Action, Effect>,
): Store<State, Action, Effect> = store(
    stateMachine = stateMachine,
    initialState = initialState,
    plugins = plugins.toList(),
    scope = viewModelScope,
).registerScoped(registry = registry)
