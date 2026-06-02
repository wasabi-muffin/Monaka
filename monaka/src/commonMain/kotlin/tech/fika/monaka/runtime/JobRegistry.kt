package tech.fika.monaka.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A map of named [Job]s owned by a single [DefaultStore] instance.
 *
 * Handlers use the keyed overload of `launch` on [HandlerScope] to start
 * cancellable background work
 * without holding a [Job] reference outside the machine.
 *
 * ### Thread-safety
 * All mutations ([launch], [cancel], [cancelAll]) are called exclusively from
 * the machine's sequential processing coroutine. No synchronisation is needed.
 */
internal class JobRegistry {

    private val jobs = mutableMapOf<String, Job>()

    /**
     * Cancel any [Job] previously registered under [key], launch [block] in [scope],
     * register the new [Job] under [key], and return it.
     */
    fun launch(scope: CoroutineScope, key: String, block: suspend CoroutineScope.() -> Unit): Job {
        jobs[key]?.cancel()
        return scope.launch(block = block).also { jobs[key] = it }
    }

    /**
     * Cancel the [Job] registered under [key], if any, and remove it from the registry.
     */
    fun cancel(key: String) {
        jobs[key]?.cancel()
        jobs.remove(key)
    }

    /**
     * Cancel every tracked [Job] and clear the registry.
     * Called when the machine itself is cancelled.
     */
    fun cancelAll() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}
