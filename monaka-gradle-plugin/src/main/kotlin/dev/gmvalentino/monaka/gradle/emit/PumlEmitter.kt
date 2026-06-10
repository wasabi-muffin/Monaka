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
        if (model.initial.isNotBlank()) appendLine("[*] --> ${model.initial}")

        val allTargets = collectAllTargets(model.states)
        val phantomNames = allTargets - model.states.keys

        // Catch-all: state whose key matches the machine name convention.
        val catchAllKey = model.states.keys.firstOrNull { it == model.name || it == "${model.name}State" }

        // Namespace grouping: states with dots are grouped under their prefix.
        // e.g. "Stable.Initial" → prefix "Stable"
        val nestedByPrefix = model.states.keys
            .filter { it.contains(".") }
            .groupBy { it.substringBeforeLast(".") }
        val nestedKeys = nestedByPrefix.values.flatten().toSet()

        // Top-level keys: not the catch-all and not dot-nested inside a namespace.
        val topLevelKeys = model.states.keys.filter { it != catchAllKey && it !in nestedKeys }

        val catchAllHasSubstates = catchAllKey != null && model.states.keys.any { it != catchAllKey }

        if (catchAllHasSubstates) {
            // ── Composite layout: catch-all wraps all other states ─────────
            val catchAllNode = model.states[catchAllKey]!!
            appendLine()
            appendLine("state \"$catchAllKey\" as $catchAllKey {")
            emitStateLines(catchAllKey, catchAllNode, indent = "  ")
            for (key in topLevelKeys) {
                appendLine()
                if (key in nestedByPrefix) {
                    emitNamespaceComposite(key, model.states[key], nestedByPrefix[key]!!, model.states, phantomNames, "  ")
                } else {
                    appendLine("  state \"$key\" as $key")
                    emitStateLines(key, model.states[key]!!, indent = "  ")
                }
            }
            for ((prefix, children) in nestedByPrefix.filter { it.key !in model.states }) {
                appendLine()
                emitNamespaceComposite(prefix, null, children, model.states, phantomNames, "  ")
            }
            for (phantom in phantomNames.filter { !it.contains(".") }) {
                appendLine()
                appendLine("  state \"$phantom\" as $phantom")
            }
            appendLine("}")
        } else {
            // ── Flat / mixed layout ────────────────────────────────────────
            appendLine()
            // Single catch-all with no substates (e.g. flat Counter machine).
            if (catchAllKey != null) {
                val node = model.states[catchAllKey]!!
                appendLine("state \"$catchAllKey\" as $catchAllKey")
                emitStateLines(catchAllKey, node, indent = "")
                appendLine()
            }
            for (key in topLevelKeys) {
                if (key in nestedByPrefix) {
                    emitNamespaceComposite(key, model.states[key], nestedByPrefix[key]!!, model.states, phantomNames, "")
                } else {
                    appendLine("state \"$key\" as $key")
                    emitStateLines(key, model.states[key]!!, indent = "")
                }
                appendLine()
            }
            // Implicit namespace prefixes not directly in model.states.
            for ((prefix, children) in nestedByPrefix.filter { it.key !in model.states }) {
                emitNamespaceComposite(prefix, null, children, model.states, phantomNames, "")
                appendLine()
            }
            for (phantom in phantomNames.filter { !it.contains(".") }) {
                appendLine("state \"$phantom\" as $phantom")
                appendLine()
            }
        }

        appendLine()
        append("@enduml")
    }

    private fun StringBuilder.emitNamespaceComposite(
        prefix: String,
        prefixNode: StateNode?,
        childKeys: List<String>,
        allStates: Map<String, StateNode>,
        phantomNames: Set<String>,
        indent: String,
    ) {
        appendLine("${indent}state \"$prefix\" as $prefix {")
        prefixNode?.let { emitStateLines(prefix, it, indent = "$indent  ") }
        for (childKey in childKeys) {
            val childNode = allStates[childKey] ?: continue
            appendLine()
            appendLine("$indent  state \"${childKey.substringAfterLast(".")}\" as $childKey")
            emitStateLines(childKey, childNode, indent = "$indent  ")
        }
        for (phantom in phantomNames.filter { it.startsWith("$prefix.") }) {
            appendLine()
            appendLine("$indent  state \"${phantom.substringAfterLast(".")}\" as $phantom")
        }
        appendLine("${indent}}")
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
