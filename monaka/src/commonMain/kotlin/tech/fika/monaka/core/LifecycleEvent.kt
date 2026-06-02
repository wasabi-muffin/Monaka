package tech.fika.monaka.core

/**
 * Application lifecycle events that can be forwarded to a state machine to trigger
 * state-scoped lifecycle hooks registered via the DSL.
 *
 * Wire these up from your platform's lifecycle callbacks:
 *
 * ### Android (ViewModel + Lifecycle)
 * ```kotlin
 * class MyViewModel : ViewModel() {
 *     val machine = MyStateMachine(viewModelScope)
 *
 *     init {
 *         // ProcessLifecycleOwner covers the whole app process:
 *         ProcessLifecycleOwner.get().lifecycle.addObserver(
 *             object : DefaultLifecycleObserver {
 *                 override fun onResume(owner: LifecycleOwner) = machine.onLifecycleEvent(LifecycleEvent.OnResume)
 *                 override fun onPause(owner: LifecycleOwner)  = machine.onLifecycleEvent(LifecycleEvent.OnPause)
 *             }
 *         )
 *     }
 * }
 * ```
 *
 * ### iOS (SwiftUI / UIKit)
 * Observe `UIApplication.willResignActiveNotification` and
 * `UIApplication.didBecomeActiveNotification`, then call the Kotlin machine from Swift.
 */
enum class LifecycleEvent {
    OnCreate,
    OnStart,
    OnResume,
    OnPause,
    OnStop,
    OnDestroy,
}
