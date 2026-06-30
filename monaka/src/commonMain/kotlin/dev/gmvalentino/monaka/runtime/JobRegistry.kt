package dev.gmvalentino.monaka.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tracks background [Job]s owned by a single [DefaultStore] instance.
 *
 * Handlers use the `task` family on `HandlerScope` to start background work. Keyed tasks
 * replace any prior job with the same key; tasks flagged `autoCancel = true` are also
 * canceled by [cancelAutoCancellable] when the state type changes.
 *
 * ### Thread-safety
 * All mutations are called exclusively from the machine's sequential processing coroutine.
 * No synchronization is needed.
 */
@OptIn(ExperimentalUuidApi::class)
internal class JobRegistry {

    private data class Entry(val job: Job, val autoCancel: Boolean)

    private val keyed = mutableMapOf<String, Entry>()

    /**
     * Cancel any [Job] previously registered under [key] (defaults to a fresh UUID for
     * untracked, fire-and-forget work), launch [block] in [scope], register the new [Job]
     * under [key], and return it.
     *
     * When [autoCancel] is true, the job is additionally canceled and its key unregistered
     * by [cancelAutoCancellable] on the next state-type change. Anonymous (UUID-keyed) jobs
     * remove themselves from the registry on completion so the map does not grow unbounded.
     */
    fun launch(
        scope: CoroutineScope,
        key: String = Uuid.random().toString(),
        autoCancel: Boolean = false,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        keyed[key]?.job?.cancel()
        val job = scope.launch(block = block)
        keyed[key] = Entry(job = job, autoCancel = autoCancel)
        job.invokeOnCompletion { if (keyed[key]?.job === job) keyed.remove(key) }
        return job
    }

    /**
     * Cancel the [Job] registered under [key], if any, and remove it from the registry.
     */
    fun cancel(key: String) {
        keyed[key]?.job?.cancel()
        keyed.remove(key)
    }

    /**
     * Cancel every tracked job flagged as auto-cancellable and remove them from the registry.
     * Called by the runtime before firing `onExit` when the state type changes.
     */
    fun cancelAutoCancellable() {
        val iterator = keyed.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.autoCancel) {
                entry.value.job.cancel()
                iterator.remove()
            }
        }
    }

    /**
     * Cancel every tracked [Job] and clear the registry.
     * Called when the machine itself is canceled.
     */
    fun cancelAll() {
        keyed.values.forEach { it.job.cancel() }
        keyed.clear()
    }
}
