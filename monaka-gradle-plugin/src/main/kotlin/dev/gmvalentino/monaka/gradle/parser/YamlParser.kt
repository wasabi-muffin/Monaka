package dev.gmvalentino.monaka.gradle.parser

import dev.gmvalentino.monaka.gradle.model.HandlerModel
import dev.gmvalentino.monaka.gradle.model.HookModel
import dev.gmvalentino.monaka.gradle.model.MachineModel
import dev.gmvalentino.monaka.gradle.model.StateNode
import dev.gmvalentino.monaka.gradle.model.TaskModel
import org.yaml.snakeyaml.Yaml

/**
 * Parses the YAML format produced by [dev.gmvalentino.monaka.gradle.emit.YamlWriter]
 * back into a [MachineModel] using SnakeYAML for structural parsing.
 */
class YamlParser {

    fun parse(content: String): MachineModel {
        @Suppress("UNCHECKED_CAST")
        val root = Yaml().load<Map<String, Any?>>(content) ?: return MachineModel("", "")
        val name = root["name"] as? String ?: ""
        val initial = root["initial"] as? String ?: ""

        @Suppress("UNCHECKED_CAST")
        val statesRaw = root["states"] as? Map<String, Any?> ?: emptyMap()
        val states = statesRaw.mapValues { (_, v) -> parseStateNode(v) }
        return MachineModel(name = name, initial = initial, states = states)
    }

    // ── State ─────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseStateNode(value: Any?): StateNode {
        val map = value as? Map<String, Any?> ?: return StateNode()
        val onEnter = parseHookOrNull(map["onEnter"])
        val onExit = parseHookOrNull(map["onExit"])
        val onUpdate = parseHookOrNull(map["onUpdate"])
        val lifecycleHooks = LIFECYCLE_EVENTS
            .mapNotNull { event -> parseHookOrNull(map[event])?.let { event to it } }
            .toMap()
        val reserved = setOf("onEnter", "onExit", "onUpdate") + LIFECYCLE_EVENTS
        val on = map.filterKeys { it !in reserved }
            .mapValues { (_, v) -> parseHandler(v) }
        return StateNode(
            onEnter = onEnter,
            onExit = onExit,
            onUpdate = onUpdate,
            lifecycleHooks = lifecycleHooks,
            on = on,
        )
    }

    // ── Hook ──────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseHookOrNull(value: Any?): HookModel? {
        if (value == null) return null
        val map = value as? Map<String, Any?> ?: return HookModel()
        return HookModel(
            task = parseTaskOrNull(map["task"]),
            transitions = stringList(map["transition"]),
            effects = stringList(map["effect"]),
            dispatches = stringList(map["dispatch"]),
        )
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseHandler(value: Any?): HandlerModel {
        val map = value as? Map<String, Any?> ?: return HandlerModel()
        return HandlerModel(
            task = parseTaskOrNull(map["task"]),
            transitions = stringList(map["transition"]),
            effects = stringList(map["effect"]),
            dispatches = stringList(map["dispatch"]),
            reject = map["reject"] as? Boolean ?: false,
        )
    }

    // ── Task ──────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseTaskOrNull(value: Any?): TaskModel? {
        if (value == null) return null
        val map = value as? Map<String, Any?> ?: return TaskModel()
        return TaskModel(
            key = map["key"] as? String,
            autoCancel = map["autoCancel"] as? Boolean ?: false,
            dispatches = stringList(map["dispatch"]),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun stringList(value: Any?): List<String> = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    companion object {
        private val LIFECYCLE_EVENTS = setOf(
            "onResume",
            "onPause",
            "onStart",
            "onStop",
            "onCreate",
            "onDestroy",
        )
    }
}
