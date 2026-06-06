package tech.fika.monaka.gradle.emit

import tech.fika.monaka.gradle.model.*

class YamlEmitter {

    fun emit(model: MachineModel): String = buildString {
        appendLine("name: ${model.name}")

        appendLine("initial: ${model.initial}")

        val flat = flatten(model.states)
        if (flat.isNotEmpty()) {
            appendLine("states:")
            for ((path, node) in flat) {
                append(emitState(path, node))
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

    private fun emitState(path: String, node: StateNode): String = buildString {
        val pad = "  "
        val empty = node.onEnter == null && node.onExit == null &&
                node.lifecycleHooks.isEmpty() && node.on.isEmpty()

        if (empty) {
            appendLine("$pad$path: {}")
            return@buildString
        }

        appendLine("$pad$path:")
        node.onEnter?.let { append(emitHook("onEnter", it, node)) }
        node.onExit?.let { append(emitHook("onExit", it, node)) }
        for ((event, hook) in node.lifecycleHooks) {
            append(emitHook(event, hook, node))
        }
        for ((action, handler) in node.on) {
            append(emitHandler(action, handler))
        }
    }

    // ── Hook ─────────────────────────────────────────────────────────────────

    private fun emitHook(key: String, hook: HookModel, state: StateNode): String = buildString {
        val pad = "    "
        val transitions = reachableTransitions(hook, state)

        val hasContent = hook.task != null || transitions.isNotEmpty() || hook.effects.isNotEmpty()
        if (!hasContent) {
            appendLine("$pad$key: {}")
            return@buildString
        }

        appendLine("$pad$key:")
        hook.task?.let { append(emitTask(it, "$pad  ")) }
        if (transitions.isNotEmpty()) appendLine("$pad  transition: ${inlineList(transitions)}")
        if (hook.effects.isNotEmpty()) appendLine("$pad  effect: ${inlineList(hook.effects)}")
    }

    /**
     * States reachable from a hook:
     * 1. A direct `transition { Target }` inside the hook body.
     * 2. For each action the task dispatches: look up that action's handler in this state
     *    and take its transition target.
     * 3. Same resolution for a direct `dispatch(Action)` in the hook.
     */
    private fun reachableTransitions(hook: HookModel, state: StateNode): List<String> =
        buildList {
            addAll(hook.transitions)
            hook.task?.dispatches?.forEach { dispatched ->
                state.on[dispatched.substringAfterLast(".")]?.transition?.let { add(it) }
            }
            hook.dispatch?.let { dispatched ->
                state.on[dispatched.substringAfterLast(".")]?.transition?.let { add(it) }
            }
        }.distinct()

    // ── Handler ───────────────────────────────────────────────────────────────

    private fun emitHandler(name: String, h: HandlerModel): String = buildString {
        val pad = "    "
        val empty = h.task == null && h.transition == null &&
                h.effects.isEmpty() && h.dispatch == null

        if (empty) {
            appendLine("$pad$name: {}")
            return@buildString
        }

        appendLine("$pad$name:")
        h.task?.let { append(emitTask(it, "$pad  ")) }
        h.transition?.let { appendLine("$pad  transition: ${inlineList(listOf(it))}") }
        if (h.effects.isNotEmpty()) appendLine("$pad  effect: ${inlineList(h.effects)}")
        h.dispatch?.let { appendLine("$pad  dispatch: ${inlineList(listOf(it))}") }
    }

    // ── Task ─────────────────────────────────────────────────────────────────

    private fun emitTask(task: TaskModel, basePad: String): String = buildString {
        val dispatches = task.dispatches.map { it.substringAfterLast(".") }
        val hasContent = task.key != null || task.autoCancel || dispatches.isNotEmpty()

        if (!hasContent) {
            appendLine("${basePad}task: {}")
            return@buildString
        }

        appendLine("${basePad}task:")
        task.key?.let { appendLine("${basePad}  key: $it") }
        if (task.autoCancel) appendLine("${basePad}  autoCancel: true")
        if (dispatches.isNotEmpty()) appendLine("${basePad}  dispatch: ${inlineList(dispatches)}")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun inlineList(items: List<String>): String = "[${items.joinToString(", ")}]"
}
