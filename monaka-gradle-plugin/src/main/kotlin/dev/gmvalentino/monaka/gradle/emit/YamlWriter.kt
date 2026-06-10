package dev.gmvalentino.monaka.gradle.write

import dev.gmvalentino.monaka.gradle.model.HandlerModel
import dev.gmvalentino.monaka.gradle.model.HookModel
import dev.gmvalentino.monaka.gradle.model.MachineModel
import dev.gmvalentino.monaka.gradle.model.StateNode
import dev.gmvalentino.monaka.gradle.model.TaskModel

class YamlWriter {

    fun write(model: MachineModel): String = buildString {
        appendLine("name: ${model.name}")
        appendLine("initial: ${model.initial}")

        val flattenedStates = flatten(model.states)
        if (flattenedStates.isNotEmpty()) {
            appendLine("states:")
            for ((path, node) in flattenedStates) {
                append(writeState(path, node))
            }
        }
    }

    // ── Flatten hierarchy ─────────────────────────────────────────────────────

    /**
     * Depth-first traversal of the state tree producing (full-dot-path, node) pairs
     * so every state is a top-level key under `states:`.
     */
    private fun flatten(
        states: Map<String, StateNode>,
        prefix: String = "",
    ): List<Pair<String, StateNode>> = buildList {
        for ((name, node) in states) {
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            add(path to node)
            addAll(flatten(node.states, path))
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private fun writeState(path: String, node: StateNode): String = buildString {
        val pad = "  "
        val isEmpty = node.onEnter == null && node.onExit == null && node.onUpdate == null && node.lifecycleHooks.isEmpty() && node.on.isEmpty()

        if (isEmpty) {
            appendLine("$pad$path: {}")
        } else {
            appendLine("$pad$path:")
            node.onEnter?.let { append(writeHook("onEnter", it)) }
            node.onExit?.let { append(writeHook("onExit", it)) }
            node.onUpdate?.let { append(writeHook("onUpdate", it)) }
            for ((event, hook) in node.lifecycleHooks) {
                append(writeHook(event, hook))
            }
            for ((action, handler) in node.on) {
                append(writeHandler(action, handler))
            }
        }
    }

    // ── Hook ─────────────────────────────────────────────────────────────────

    private fun writeHook(key: String, hook: HookModel): String = buildString {
        val pad = "    "
        val transitions = reachableTransitions(hook)

        val hasContent = hook.task != null || transitions.isNotEmpty() || hook.effects.isNotEmpty() || hook.dispatch != null
        if (!hasContent) {
            appendLine("$pad$key: {}")
        } else {
            appendLine("$pad$key:")
            hook.task?.let { append(writeTask(it, "$pad  ")) }
            if (transitions.isNotEmpty()) appendLine("$pad  transition: ${inlineList(transitions)}")
            if (hook.effects.isNotEmpty()) appendLine("$pad  effect: ${inlineList(hook.effects)}")
            hook.dispatch?.let { appendLine("$pad  dispatch: ${inlineList(listOf(it))}") }
        }
    }

    /**
     * States reachable from a hook: only explicit `transition(Target)` calls recorded
     * directly in the hook body. Dispatch-based inference is intentionally excluded —
     * dispatches (both direct and inside task coroutines) are async side-effects whose
     * eventual transition targets should not be attributed to the hook itself.
     */
    private fun reachableTransitions(hook: HookModel): List<String> =
        hook.transitions.distinct()

    // ── Handler ───────────────────────────────────────────────────────────────

    private fun writeHandler(name: String, handler: HandlerModel): String = buildString {
        val pad = "    "
        val empty = handler.task == null && handler.transitions.isEmpty() && handler.effects.isEmpty() && handler.dispatch == null
        if (empty) {
            appendLine("$pad$name: {}")
        } else {
            appendLine("$pad$name:")
            handler.task?.let { append(writeTask(it, "$pad  ")) }
            if (handler.transitions.isNotEmpty()) appendLine("$pad  transition: ${inlineList(handler.transitions)}")
            if (handler.effects.isNotEmpty()) appendLine("$pad  effect: ${inlineList(handler.effects)}")
            handler.dispatch?.let { appendLine("$pad  dispatch: ${inlineList(listOf(it))}") }
        }
    }

    // ── Task ─────────────────────────────────────────────────────────────────

    private fun writeTask(task: TaskModel, basePad: String): String = buildString {
        val dispatches = task.dispatches.map { it.substringAfterLast(".") }
        val hasContent = task.key != null || task.autoCancel || dispatches.isNotEmpty()

        if (!hasContent) {
            appendLine("${basePad}task: {}")
        } else {
            appendLine("${basePad}task:")
            task.key?.let { appendLine("$basePad  key: $it") }
            if (task.autoCancel) appendLine("$basePad  autoCancel: true")
            if (dispatches.isNotEmpty()) appendLine("$basePad  dispatch: ${inlineList(dispatches)}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun inlineList(items: List<String>): String = "[ ${items.joinToString(", ")} ]"
}
