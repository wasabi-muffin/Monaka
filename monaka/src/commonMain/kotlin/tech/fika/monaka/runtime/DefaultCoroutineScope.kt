package tech.fika.monaka.runtime

import kotlinx.coroutines.CoroutineScope

/**
 * Creates a new default [CoroutineScope] for a [tech.fika.monaka.core.Store] that has not been
 * given an explicit scope.
 *
 * Each call returns a fresh, independent scope. Callers are responsible for cancelling it
 * (via [tech.fika.monaka.core.Store.cancel]) when the store is no longer needed.
 *
 * Platform implementations:
 * - **Android / iOS** — `SupervisorJob() + Dispatchers.Main.immediate`, matching the convention
 *   used by `viewModelScope` and Compose Multiplatform host controllers.
 * - **JVM** — `SupervisorJob() + Dispatchers.Default`, because a standalone JVM process has no
 *   guaranteed main-thread dispatcher without an explicit dependency such as
 *   `kotlinx-coroutines-swing` or `kotlinx-coroutines-javafx`.
 */
internal expect fun defaultCoroutineScope(): CoroutineScope
