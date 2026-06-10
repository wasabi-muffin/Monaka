package dev.gmvalentino.monaka.gradle.emit

import dev.gmvalentino.monaka.gradle.model.*

/**
 * Emits a PlantUML state diagram from a [MachineModel].
 *
 * Format conventions:
 *  - Description lines (`State : trigger → target ◆ effects`) carry all behaviour detail.
 *  - Arrows (`State --> target : trigger`) carry only routing — no effects.
 *  - `onEnter` with an immediate transition uses a dashed arrow; `onEnter` with a task uses
 *    a description line only (the transition happens later when the task dispatches an action).
 *  - Lifecycle hooks (onPause, onResume, …) get both a description line and a solid arrow.
 *  - States that never appear as a transition target are treated as catch-all / parent states
 *    and rendered as a composite PlantUML state wrapping all leaf states.
 *  - Transition targets that have no handlers in the model (phantom states) are declared as
 *    empty states inside the composite block, or at the top level for flat machines.
 */
class PumlEmitter {

    fun emit(model: MachineModel): String = buildString {
        appendLine("@startuml ${model.name}")
        appendLine("hide empty description")
        appendLine("title ${model.name}")
        appendLine()
        appendLine("[*] --> ${model.initial}")

        val allTargets = collectAllTargets(model.states)
        val parentNames = model.states.keys.filter { it !in allTargets }.toSet()
        val leafStates = model.states.filterKeys { it !in parentNames }
        val phantomNames = allTargets - model.states.keys

        if (parentNames.isEmpty()) {
            // ── Flat layout ────────────────────────────────────────────────
            appendLine()
            for ((name, node) in model.states) {
                appendLine("state \"$name\" as $name")
                emitStateLines(name, node, indent = "")
                appendLine()
            }
            for (name in phantomNames) {
                appendLine("state \"$name\" as $name")
                appendLine()
            }
        } else {
            // ── Composite layout ───────────────────────────────────────────
            // Each catch-all state wraps all leaf + phantom states.
            for (parentName in parentNames) {
                val parentNode = model.states[parentName] ?: continue
                appendLine()
                appendLine("state \"$parentName\" as $parentName {")
                emitStateLines(parentName, parentNode, indent = "  ")
                for ((childName, childNode) in leafStates) {
                    appendLine()
                    appendLine("  state \"$childName\" as $childName")
                    emitStateLines(childName, childNode, indent = "  ")
                }
                for (phantomName in phantomNames) {
                    appendLine()
                    appendLine("  state \"$phantomName\" as $phantomName")
                }
                appendLine("}")
            }
        }

        appendLine()
        append("@enduml")
    }

    // ── Per-state lines ───────────────────────────────────────────────────────

    private fun StringBuilder.emitStateLines(name: String, node: StateNode, indent: String) {
        val descLines = mutableListOf<String>()
        val arrowLines = mutableListOf<String>()

        // onEnter
        node.onEnter?.let { hook ->
            when {
                hook.task != null -> {
                    // Task fires on entry; no arrow — the transition comes later via dispatch.
                    descLines += "$indent$name : onEnter ▶ ${formatTask(hook.task)}"
                }
                else -> {
                    // Resolve direct transitions first; fall back to following dispatch chains
                    // (mirrors YamlEmitter.reachableTransitions so output is consistent).
                    val targets = reachableTransitions(hook)
                    if (targets.isNotEmpty()) {
                        val targetStr = if (targets.size == 1) targets.first()
                                        else targets.joinToString(" | ", "[", "]")
                        descLines += "$indent$name : onEnter → $targetStr${formatEffects(hook.effects)}"
                        targets.forEach { arrowLines += "$indent$name -[dashed]-> $it : onEnter" }
                    }
                }
            }
        }

        // onExit
        node.onExit?.let { hook ->
            when {
                hook.task != null -> {
                    descLines += "$indent$name : onExit ▶ ${formatTask(hook.task)}"
                }
                else -> {
                    val targets = reachableTransitions(hook)
                    if (targets.isNotEmpty()) {
                        val targetStr = if (targets.size == 1) targets.first()
                                        else targets.joinToString(" | ", "[", "]")
                        descLines += "$indent$name : onExit → $targetStr${formatEffects(hook.effects)}"
                        targets.forEach { arrowLines += "$indent$name --> $it : onExit" }
                    }
                }
            }
        }

        // onUpdate — same-type value change; show effects and any cross-state transitions,
        // but omit self-arrows since staying in the same state type adds no routing information.
        node.onUpdate?.let { hook ->
            when {
                hook.task != null -> {
                    descLines += "$indent$name : onUpdate ▶ ${formatTask(hook.task)}"
                }
                hook.effects.isNotEmpty() -> {
                    descLines += "$indent$name : onUpdate${formatEffects(hook.effects)}"
                }
                else -> {
                    val targets = reachableTransitions(hook).filter { it != name }
                    if (targets.isNotEmpty()) {
                        val targetStr = if (targets.size == 1) targets.first()
                                        else targets.joinToString(" | ", "[", "]")
                        descLines += "$indent$name : onUpdate → $targetStr${formatEffects(hook.effects)}"
                        targets.forEach { arrowLines += "$indent$name --> $it : onUpdate" }
                    }
                }
            }
        }

        // Lifecycle hooks (onPause, onResume, onStart, onStop, …)
        for ((event, hook) in node.lifecycleHooks) {
            val targets = reachableTransitions(hook)
            if (targets.isEmpty()) continue
            val targetStr = if (targets.size == 1) targets.first()
                            else targets.joinToString(" | ", "[", "]")
            descLines += "$indent$name : $event → $targetStr"
            targets.forEach { arrowLines += "$indent$name --> $it : $event" }
        }

        // Action handlers
        for ((action, handler) in node.on) {
            buildHandlerDescLine(indent, name, action, handler)?.let { descLines += it }
            handler.transitions.forEach { arrowLines += "$indent$name --> $it : $action" }
        }

        descLines.forEach { appendLine(it) }
        arrowLines.forEach { appendLine(it) }
    }

    private fun buildHandlerDescLine(
        indent: String,
        stateName: String,
        action: String,
        handler: HandlerModel,
    ): String? {
        if (handler.transitions.isEmpty() && handler.task == null && handler.effects.isEmpty()) return null
        val target = if (handler.transitions.isNotEmpty()) " → ${handler.transitions.joinToString(" | ")}" else ""
        val effects = formatEffects(handler.effects)
        val task = handler.task?.let { " ▶ ${formatTask(it)}" } ?: ""
        return "$indent$stateName : $action$target$effects$task"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * States reachable from a hook: only explicit `transition(Target)` calls recorded
     * directly in the hook body. Dispatch-based inference is excluded for the same reason
     * as in YamlEmitter — dispatches are async side-effects, not direct hook outcomes.
     */
    private fun reachableTransitions(hook: HookModel): List<String> = hook.transitions.distinct()

    private fun collectAllTargets(states: Map<String, StateNode>): Set<String> = buildSet {
        for (node in states.values) {
            node.on.values.flatMap { it.transitions }.forEach { add(it) }
            node.onEnter?.transitions?.forEach { add(it) }
            node.onExit?.transitions?.forEach { add(it) }
            node.onUpdate?.transitions?.forEach { add(it) }
            node.lifecycleHooks.values.flatMap { it.transitions }.forEach { add(it) }
        }
    }

    private fun formatEffects(effects: List<String>): String =
        if (effects.isEmpty()) "" else " ◆ ${effects.joinToString(", ")}"

    private fun formatTask(task: TaskModel): String {
        val key = task.key ?: ""
        val autoCancel = if (task.autoCancel) ", autoCancel" else ""
        val dispatches = task.dispatches
            .map { it.substringAfterLast(".") }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" | ")
            ?.let { "{ $it }" }
            ?: ""
        return "task($key$autoCancel)$dispatches"
    }
}
