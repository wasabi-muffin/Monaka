package dev.gmvalentino.monaka.gradle.parser

import dev.gmvalentino.monaka.gradle.model.*

/**
 * Parses the fixed-indentation YAML format produced by [dev.gmvalentino.monaka.gradle.emit.YamlEmitter]
 * back into a [MachineModel].
 *
 * Indent contract (all two-space steps):
 *   0  — top-level keys (name, initial, states)
 *   2  — state path keys
 *   4  — hook / action keys within a state
 *   6  — properties of a hook or action (transition, effect, dispatch, task)
 *   8  — properties of a task block
 */
class YamlParser {

    fun parse(content: String): MachineModel {
        val lines = content.lines()
        var name = ""
        var initial = ""
        val states = LinkedHashMap<String, StateNode>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("name:") -> {
                    name = trimmed.removePrefix("name:").trim()
                    i++
                }
                trimmed.startsWith("initial:") -> {
                    initial = trimmed.removePrefix("initial:").trim()
                    i++
                }
                trimmed == "states:" -> {
                    i++
                    while (i < lines.size) {
                        val stateLine = lines[i]
                        if (stateLine.isBlank()) { i++; continue }
                        if (indentOf(stateLine) != 2) break
                        val stateEntry = stateLine.trim()
                        when {
                            stateEntry.endsWith(": {}") -> {
                                states[stateEntry.removeSuffix(": {}")] = StateNode()
                                i++
                            }
                            stateEntry.endsWith(":") -> {
                                val key = stateEntry.removeSuffix(":")
                                i++
                                val (node, next) = parseStateNode(lines, i)
                                states[key] = node
                                i = next
                            }
                            else -> i++
                        }
                    }
                }
                else -> i++
            }
        }

        return MachineModel(name = name, initial = initial, states = states)
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private fun parseStateNode(lines: List<String>, start: Int): Pair<StateNode, Int> {
        var i = start
        var onEnter: HookModel? = null
        var onExit: HookModel? = null
        var onUpdate: HookModel? = null
        val lifecycleHooks = LinkedHashMap<String, HookModel>()
        val on = LinkedHashMap<String, HandlerModel>()

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            if (indentOf(line) != 4) break

            val entry = line.trim()
            when {
                entry.startsWith("onEnter:") -> {
                    if (entry == "onEnter:") {
                        i++; val (h, next) = parseHookModel(lines, i); onEnter = h; i = next
                    } else {
                        onEnter = HookModel(); i++
                    }
                }
                entry.startsWith("onExit:") -> {
                    if (entry == "onExit:") {
                        i++; val (h, next) = parseHookModel(lines, i); onExit = h; i = next
                    } else {
                        onExit = HookModel(); i++
                    }
                }
                entry.startsWith("onUpdate:") -> {
                    if (entry == "onUpdate:") {
                        i++; val (h, next) = parseHookModel(lines, i); onUpdate = h; i = next
                    } else {
                        onUpdate = HookModel(); i++
                    }
                }
                LIFECYCLE_EVENTS.any { entry.startsWith("$it:") } -> {
                    val event = entry.substringBefore(":")
                    if (entry == "$event:") {
                        i++; val (h, next) = parseHookModel(lines, i); lifecycleHooks[event] = h; i = next
                    } else {
                        lifecycleHooks[event] = HookModel(); i++
                    }
                }
                entry.endsWith(": {}") -> {
                    on[entry.removeSuffix(": {}")] = HandlerModel(); i++
                }
                entry.endsWith(":") -> {
                    val action = entry.removeSuffix(":")
                    i++; val (h, next) = parseHandlerModel(lines, i); on[action] = h; i = next
                }
                else -> i++
            }
        }

        return StateNode(onEnter = onEnter, onExit = onExit, onUpdate = onUpdate, lifecycleHooks = lifecycleHooks, on = on) to i
    }

    // ── Hook ──────────────────────────────────────────────────────────────────

    private fun parseHookModel(lines: List<String>, start: Int): Pair<HookModel, Int> {
        var i = start
        var task: TaskModel? = null
        val transitions = mutableListOf<String>()
        val effects = mutableListOf<String>()
        var dispatch: String? = null

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            if (indentOf(line) != 6) break

            val entry = line.trim()
            when {
                entry.startsWith("task:") -> {
                    if (entry == "task:") {
                        i++; val (t, next) = parseTaskModel(lines, i); task = t; i = next
                    } else {
                        task = TaskModel(); i++
                    }
                }
                entry.startsWith("transition:") ->
                    { transitions += parseInlineList(entry.removePrefix("transition:").trim()); i++ }
                entry.startsWith("effect:") ->
                    { effects += parseInlineList(entry.removePrefix("effect:").trim()); i++ }
                entry.startsWith("dispatch:") ->
                    { dispatch = parseInlineList(entry.removePrefix("dispatch:").trim()).firstOrNull(); i++ }
                else -> i++
            }
        }

        return HookModel(task = task, effects = effects, transitions = transitions, dispatch = dispatch) to i
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    private fun parseHandlerModel(lines: List<String>, start: Int): Pair<HandlerModel, Int> {
        var i = start
        var task: TaskModel? = null
        var transition: String? = null
        val effects = mutableListOf<String>()
        var dispatch: String? = null
        var reject = false

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            if (indentOf(line) != 6) break

            val entry = line.trim()
            when {
                entry.startsWith("task:") -> {
                    if (entry == "task:") {
                        i++; val (t, next) = parseTaskModel(lines, i); task = t; i = next
                    } else {
                        task = TaskModel(); i++
                    }
                }
                entry.startsWith("transition:") ->
                    { transition = parseInlineList(entry.removePrefix("transition:").trim()).firstOrNull(); i++ }
                entry.startsWith("effect:") ->
                    { effects += parseInlineList(entry.removePrefix("effect:").trim()); i++ }
                entry.startsWith("dispatch:") ->
                    { dispatch = parseInlineList(entry.removePrefix("dispatch:").trim()).firstOrNull(); i++ }
                entry == "reject: true" -> { reject = true; i++ }
                else -> i++
            }
        }

        return HandlerModel(
            transitions = listOfNotNull(transition), effects = effects,
            reject = reject, dispatch = dispatch, task = task,
        ) to i
    }

    // ── Task ──────────────────────────────────────────────────────────────────

    private fun parseTaskModel(lines: List<String>, start: Int): Pair<TaskModel, Int> {
        var i = start
        var key: String? = null
        var autoCancel = false
        val dispatches = mutableListOf<String>()

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            if (indentOf(line) != 8) break

            val entry = line.trim()
            when {
                entry.startsWith("key:") -> { key = entry.removePrefix("key:").trim(); i++ }
                entry == "autoCancel: true" -> { autoCancel = true; i++ }
                entry.startsWith("dispatch:") ->
                    { dispatches += parseInlineList(entry.removePrefix("dispatch:").trim()); i++ }
                else -> i++
            }
        }

        return TaskModel(key = key, autoCancel = autoCancel, dispatches = dispatches) to i
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun indentOf(line: String): Int = line.length - line.trimStart().length

    private fun parseInlineList(value: String): List<String> =
        value.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        private val LIFECYCLE_EVENTS = setOf(
            "onResume", "onPause", "onStart", "onStop", "onCreate", "onDestroy",
        )
    }
}
