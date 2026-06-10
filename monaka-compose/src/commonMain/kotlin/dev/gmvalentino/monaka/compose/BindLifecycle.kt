package dev.gmvalentino.monaka.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent as MonakaLifecycle
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store

/**
 * Observe the platform lifecycle and forward each event to this [Store] as a
 * [dev.gmvalentino.monaka.core.LifecycleEvent], enabling `onResume`, `onPause`, and other
 * lifecycle hooks registered in the state machine to fire automatically.
 *
 * Uses `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`, JetBrains'
 * multiplatform fork of AndroidX lifecycle. The same `LocalLifecycleOwner` works
 * on Android (host Activity/Fragment) and iOS (provided by `ComposeUIViewController`).
 */
@Composable
public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.bindLifecycle(): Store<State, Action, Effect> {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, this) {
        val observer = LifecycleEventObserver { _, event ->
            event.toMonakaLifecycleEvent()?.let(::onLifecycleEvent)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return this
}

private fun Lifecycle.Event.toMonakaLifecycleEvent() = when (this) {
    Lifecycle.Event.ON_CREATE -> MonakaLifecycle.OnCreate
    Lifecycle.Event.ON_START -> MonakaLifecycle.OnStart
    Lifecycle.Event.ON_RESUME -> MonakaLifecycle.OnResume
    Lifecycle.Event.ON_PAUSE -> MonakaLifecycle.OnPause
    Lifecycle.Event.ON_STOP -> MonakaLifecycle.OnStop
    Lifecycle.Event.ON_DESTROY -> MonakaLifecycle.OnDestroy
    else -> null
}
