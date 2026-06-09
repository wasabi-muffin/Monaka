package dev.gmvalentino.monaka.ext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.StateMachine
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.plugin.Plugin
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.gmvalentino.monaka.runtime.registerScoped

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
