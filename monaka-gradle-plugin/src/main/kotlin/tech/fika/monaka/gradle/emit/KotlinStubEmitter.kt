package tech.fika.monaka.gradle.emit

import tech.fika.monaka.gradle.StubStyle
import tech.fika.monaka.gradle.model.*

class KotlinStubEmitter {

    data class GeneratedFile(val name: String, val content: String)

    fun emit(model: MachineModel, style: StubStyle, pkg: String?): List<GeneratedFile> {
        val rootName = "${model.name}State"
        val actionName = "${model.name}Action"
        val effectName = "${model.name}Effect"

        val stateTree = buildStateTree(model, rootName)
        val isSingle = stateTree.children.isEmpty()
        val actions = collectActions(model)
        val effects = collectEffects(model)

        return listOf(
            GeneratedFile("$rootName.kt", emitStateFile(model, rootName, stateTree, isSingle, pkg)),
            GeneratedFile("$actionName.kt", emitSealedFile(actionName, "Action", actions, pkg)),
            GeneratedFile("$effectName.kt", emitSealedFile(effectName, "Effect", effects, pkg)),
            GeneratedFile("${model.name}StateMachine.kt",
                emitStateMachineFile(model, rootName, actionName, effectName, isSingle, style, pkg)),
        )
    }

    // ── State tree ────────────────────────────────────────────────────────────

    private data class StateNode(
        val name: String,
        val children: LinkedHashMap<String, StateNode> = LinkedHashMap(),
    ) {
        val isLeaf get() = children.isEmpty()
    }

    private fun buildStateTree(model: MachineModel, rootName: String): StateNode {
        val root = StateNode(rootName)
        val paths = collectStatePaths(model, rootName)
        for (path in paths) {
            var current = root
            for (part in path.split(".")) {
                current = current.children.getOrPut(part) { StateNode(part) }
            }
        }
        return root
    }

    private fun collectStatePaths(model: MachineModel, rootName: String): LinkedHashSet<String> {
        val paths = LinkedHashSet<String>()
        for ((key, node) in model.states) {
            if (key != rootName) paths.add(key)
            node.onEnter?.transitions?.forEach { if (it != rootName) paths.add(it) }
            node.onExit?.transitions?.forEach { if (it != rootName) paths.add(it) }
            node.lifecycleHooks.values.forEach { h -> h.transitions.forEach { if (it != rootName) paths.add(it) } }
            node.on.values.forEach { h -> h.transition?.let { if (it != rootName) paths.add(it) } }
        }
        return paths
    }

    // ── State file ────────────────────────────────────────────────────────────

    private fun emitStateFile(
        model: MachineModel,
        rootName: String,
        tree: StateNode,
        isSingle: Boolean,
        pkg: String?,
    ): String = buildString {
        pkg?.let { appendLine("package $it\n") }
        appendLine("import tech.fika.monaka.core.State\n")
        appendLine("sealed interface $rootName : State {")
        if (isSingle) {
            appendLine("    data object ${model.name} : $rootName")
        } else {
            for ((_, child) in tree.children) {
                append(emitStateNode(child, rootName, "    "))
            }
        }
        appendLine("}")
    }

    private fun emitStateNode(node: StateNode, parentType: String, indent: String): String = buildString {
        if (node.isLeaf) {
            appendLine("${indent}data object ${node.name} : $parentType")
        } else {
            appendLine("${indent}sealed interface ${node.name} : $parentType {")
            for ((_, child) in node.children) {
                append(emitStateNode(child, node.name, "$indent    "))
            }
            appendLine("${indent}}")
        }
    }

    // ── Action / Effect files ─────────────────────────────────────────────────

    private fun emitSealedFile(
        typeName: String,
        marker: String,
        entries: Set<String>,
        pkg: String?,
    ): String = buildString {
        pkg?.let { appendLine("package $it\n") }
        appendLine("import tech.fika.monaka.core.$marker\n")
        if (entries.isEmpty()) {
            appendLine("sealed interface $typeName : $marker")
        } else {
            appendLine("sealed interface $typeName : $marker {")
            for (entry in entries) {
                appendLine("    data object $entry : $typeName")
            }
            appendLine("}")
        }
    }

    // ── StateMachine file ─────────────────────────────────────────────────────

    private fun emitStateMachineFile(
        model: MachineModel,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        style: StubStyle,
        pkg: String?,
    ): String = buildString {
        pkg?.let { appendLine("package $it\n") }
        appendLine("import tech.fika.monaka.dsl.StateMachine")
        appendLine("import tech.fika.monaka.dsl.stateMachine\n")

        val initialRef = model.initial
            .takeIf { it.isNotEmpty() }
            ?.let { transitionRef(it, rootName, isSingle, model.name) }

        // Catch-all (root key) first, then other states in yaml order
        val catchAll = model.states.entries.filter { (k, _) -> k == rootName }
        val rest = model.states.entries.filter { (k, _) -> k != rootName }
        val orderedStates = catchAll + rest

        when (style) {
            StubStyle.CLASS -> {
                appendLine("class ${model.name}StateMachine : StateMachine<$rootName, $actionName, $effectName> by stateMachine(")
                appendLine("    builder = {")
                initialRef?.let { appendLine("        initialState($it)") }
                orderedStates.forEachIndexed { index, (key, node) ->
                    if (index > 0 || initialRef != null) appendLine()
                    append(emitStateBlock(key, node, rootName, actionName, effectName, isSingle, model.name, "        "))
                }
                appendLine("    }")
                appendLine(")")
            }
            StubStyle.FACTORY -> {
                val varName = model.name.replaceFirstChar { it.lowercase() } + "StateMachine"
                appendLine("val $varName = stateMachine<$rootName, $actionName, $effectName> {")
                initialRef?.let { appendLine("    initialState($it)") }
                orderedStates.forEachIndexed { index, (key, node) ->
                    if (index > 0 || initialRef != null) appendLine()
                    append(emitStateBlock(key, node, rootName, actionName, effectName, isSingle, model.name, "    "))
                }
                appendLine("}")
            }
        }
    }

    private fun emitStateBlock(
        key: String,
        node: tech.fika.monaka.gradle.model.StateNode,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        machineName: String,
        pad: String,
    ): String = buildString {
        val typeRef = stateTypeRef(key, rootName)
        appendLine("${pad}state<$typeRef> {")
        node.onEnter?.let { append(emitHookBlock("onEnter", it, rootName, actionName, effectName, isSingle, machineName, "$pad    ")) }
        node.onExit?.let { append(emitHookBlock("onExit", it, rootName, actionName, effectName, isSingle, machineName, "$pad    ")) }
        for ((event, hook) in node.lifecycleHooks) {
            append(emitHookBlock(event, hook, rootName, actionName, effectName, isSingle, machineName, "$pad    "))
        }
        for ((action, handler) in node.on) {
            append(emitHandlerBlock(action, handler, rootName, actionName, effectName, isSingle, machineName, "$pad    "))
        }
        appendLine("${pad}}")
    }

    private fun emitHookBlock(
        hookName: String,
        hook: HookModel,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        machineName: String,
        pad: String,
    ): String = buildString {
        appendLine("${pad}$hookName {")
        hook.task?.let { append(emitTaskBlock(it, actionName, "$pad    ")) }
        hook.transitions.forEach {
            appendLine("${pad}    transition { ${transitionRef(it, rootName, isSingle, machineName)} }")
        }
        hook.effects.forEach { appendLine("${pad}    sideEffect($effectName.$it)") }
        hook.dispatch?.let { appendLine("${pad}    dispatch($it)") }
        appendLine("${pad}}")
    }

    private fun emitHandlerBlock(
        action: String,
        handler: HandlerModel,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        machineName: String,
        pad: String,
    ): String = buildString {
        appendLine("${pad}on<$actionName.$action> {")
        if (handler.reject) {
            appendLine("${pad}    reject()")
        } else {
            handler.task?.let { append(emitTaskBlock(it, actionName, "$pad    ")) }
            handler.transition?.let {
                appendLine("${pad}    transition { ${transitionRef(it, rootName, isSingle, machineName)} }")
            }
            handler.effects.forEach { appendLine("${pad}    sideEffect($effectName.$it)") }
            handler.dispatch?.let { appendLine("${pad}    dispatch($actionName.$it)") }
        }
        appendLine("${pad}}")
    }

    private fun emitTaskBlock(task: TaskModel, actionName: String, pad: String): String = buildString {
        val args = buildList {
            task.key?.let { add("\"$it\"") }
            if (task.autoCancel) add("autoCancel = true")
        }.joinToString(", ")
        appendLine("${pad}task($args) {")
        task.dispatches.forEach { appendLine("${pad}    dispatch($actionName.$it)") }
        appendLine("${pad}}")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Reference to use in `state<...>` — catch-all uses bare root, leaf uses qualified path. */
    private fun stateTypeRef(key: String, rootName: String): String =
        if (key == rootName) rootName else "$rootName.$key"

    /** Reference to use in `transition { }` and `initialState()`. */
    private fun transitionRef(target: String, rootName: String, isSingle: Boolean, machineName: String): String =
        when {
            target == rootName && isSingle -> "$rootName.$machineName"
            target == rootName -> rootName
            else -> "$rootName.$target"
        }

    // ── Collect actions / effects ─────────────────────────────────────────────

    private fun collectActions(model: MachineModel): LinkedHashSet<String> {
        val actions = LinkedHashSet<String>()
        for ((_, node) in model.states) {
            node.on.keys.filterNot { it in HOOKS }.forEach { actions.add(it) }
        }
        return actions
    }

    private fun collectEffects(model: MachineModel): LinkedHashSet<String> {
        val effects = LinkedHashSet<String>()
        fun addAll(list: List<String>) = list.forEach { effects.add(it) }
        for ((_, node) in model.states) {
            node.onEnter?.let { addAll(it.effects) }
            node.onExit?.let { addAll(it.effects) }
            node.lifecycleHooks.values.forEach { addAll(it.effects) }
            node.on.values.forEach { addAll(it.effects) }
        }
        return effects
    }

    companion object {
        private val HOOKS = setOf(
            "onEnter", "onExit",
            "onResume", "onPause", "onStart", "onStop", "onCreate", "onDestroy",
        )
    }
}
