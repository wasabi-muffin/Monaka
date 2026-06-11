package dev.gmvalentino.monaka.gradle.write

import dev.gmvalentino.monaka.gradle.StubStyle
import dev.gmvalentino.monaka.gradle.model.*

class KotlinStubWriter {

    data class GeneratedFile(val name: String, val content: String)

    fun write(
        model: MachineModel,
        style: StubStyle,
        pkg: String?,
        useTransitionAnnotation: Boolean = true,
    ): List<GeneratedFile> {
        val rootName = "${model.name}State"
        val actionName = "${model.name}Action"
        val effectName = "${model.name}Effect"

        val stateTree = buildStateTree(model, rootName)
        val isSingle = stateTree.children.isEmpty()
        val actions = collectActions(model)
        val effects = collectEffects(model)
        val transitions = if (useTransitionAnnotation) computeTransitions(model) else emptyMap()

        return listOf(
            GeneratedFile("$rootName.kt", writeStateFile(model, rootName, stateTree, isSingle, pkg, transitions)),
            GeneratedFile("$actionName.kt", writeSealedFile(actionName, "Action", actions, pkg)),
            GeneratedFile("$effectName.kt", writeSealedFile(effectName, "Effect", effects, pkg)),
            GeneratedFile("${model.name}StateMachine.kt",
                writeStateMachineFile(model, rootName, actionName, effectName, isSingle, style, pkg)),
        )
    }

    // ── Transition map ────────────────────────────────────────────────────────

    private fun computeTransitions(model: MachineModel): Map<String, List<String>> {
        val result = linkedMapOf<String, LinkedHashSet<String>>()

        fun addTarget(statePath: String, target: String?) {
            if (target != null) {
                val parentPath = statePath.substringBeforeLast(".", "")
                val reference = if (parentPath.isNotEmpty() && target.startsWith("$parentPath.")) {
                    target.removePrefix("$parentPath.")
                } else {
                    target
                }
                result.getOrPut(statePath) { linkedSetOf() }.add(reference)
            }
        }

        fun walk(states: Map<String, dev.gmvalentino.monaka.gradle.model.StateNode>) {
            for ((key, node) in states) {
                node.on.values.forEach { handler -> handler.transitions.forEach { addTarget(key, it) } }
                node.onEnter?.transitions?.forEach { addTarget(key, it) }
                node.onExit?.transitions?.forEach { addTarget(key, it) }
                node.onUpdate?.transitions?.forEach { addTarget(key, it) }
                node.lifecycleHooks.values.forEach { h -> h.transitions.forEach { addTarget(key, it) } }
                walk(node.states)
            }
        }

        walk(model.states)
        return result.mapValues { it.value.toList() }
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
            if (!isCatchAll(key, rootName, model.name)) paths.add(key)
            node.onEnter?.transitions?.forEach { if (!isCatchAll(it, rootName, model.name)) paths.add(it) }
            node.onExit?.transitions?.forEach { if (!isCatchAll(it, rootName, model.name)) paths.add(it) }
            node.onUpdate?.transitions?.forEach { if (!isCatchAll(it, rootName, model.name)) paths.add(it) }
            node.lifecycleHooks.values.forEach { h -> h.transitions.forEach { if (!isCatchAll(it, rootName, model.name)) paths.add(it) } }
            node.on.values.forEach { h -> h.transitions.forEach { if (!isCatchAll(it, rootName, model.name)) paths.add(it) } }
        }
        return paths
    }

    // ── State file ────────────────────────────────────────────────────────────

    private fun writeStateFile(
        model: MachineModel,
        rootName: String,
        tree: StateNode,
        isSingle: Boolean,
        pkg: String?,
        transitions: Map<String, List<String>>,
    ): String = buildString {
        val hasCatchAllSelfTransition = transitions.any { (key, targets) ->
            isCatchAll(key, rootName, model.name) && targets.any { isCatchAll(it, rootName, model.name) }
        }
        pkg?.let { appendLine("package $it\n") }
        if (hasCatchAllSelfTransition) appendLine("import dev.gmvalentino.monaka.core.SelfTransition")
        if (transitions.isNotEmpty()) appendLine("import dev.gmvalentino.monaka.core.Transition")
        appendLine("import dev.gmvalentino.monaka.core.State\n")
        if (hasCatchAllSelfTransition) appendLine("@SelfTransition")
        appendLine("sealed interface $rootName : State {")
        if (isSingle && model.states.isNotEmpty()) {
            appendLine("    data object ${model.name} : $rootName")
        } else {
            tree.children.entries.forEachIndexed { index, (_, child) ->
                if (index > 0) appendLine()
                append(writeStateNode(child, rootName, "    ", transitions, child.name))
            }
        }
        appendLine("}")
    }

    private fun writeStateNode(
        node: StateNode,
        parentType: String,
        indent: String,
        transitions: Map<String, List<String>>,
        fullPath: String,
    ): String = buildString {
        val targets = transitions[fullPath]
        if (!targets.isNullOrEmpty()) {
            val args = targets.joinToString(", ") { "${it}::class" }
            appendLine("${indent}@Transition($args)")
        }
        if (node.isLeaf) {
            appendLine("${indent}data object ${node.name} : $parentType")
        } else {
            appendLine("${indent}sealed interface ${node.name} : $parentType {")
            node.children.entries.forEachIndexed { index, (_, child) ->
                if (index > 0) appendLine()
                append(writeStateNode(child, node.name, "$indent    ", transitions, "$fullPath.${child.name}"))
            }
            appendLine("${indent}}")
        }
    }

    // ── Action / Effect files ─────────────────────────────────────────────────

    private data class SealedNode(
        val name: String,
        val children: LinkedHashMap<String, SealedNode> = LinkedHashMap(),
    ) {
        val isLeaf get() = children.isEmpty()
    }

    private fun buildSealedTree(entries: Set<String>): LinkedHashMap<String, SealedNode> {
        val roots = LinkedHashMap<String, SealedNode>()
        for (entry in entries) {
            var map = roots
            for (part in entry.split(".")) {
                val node = map.getOrPut(part) { SealedNode(part) }
                map = node.children
            }
        }
        return roots
    }

    private fun writeSealedFile(
        typeName: String,
        marker: String,
        entries: Set<String>,
        pkg: String?,
    ): String = buildString {
        pkg?.let { appendLine("package $it\n") }
        appendLine("import dev.gmvalentino.monaka.core.$marker\n")
        val tree = buildSealedTree(entries)
        appendLine("sealed interface $typeName : $marker {")
        for ((_, node) in tree) {
            append(writeSealedNode(node, typeName, "    "))
        }
        appendLine("}")
    }

    private fun writeSealedNode(node: SealedNode, parentType: String, indent: String): String = buildString {
        if (node.isLeaf) {
            appendLine("${indent}data object ${node.name} : $parentType")
        } else {
            appendLine("${indent}sealed interface ${node.name} : $parentType {")
            for ((_, child) in node.children) {
                append(writeSealedNode(child, node.name, "$indent    "))
            }
            appendLine("${indent}}")
        }
    }

    // ── StateMachine file ─────────────────────────────────────────────────────

    private fun writeStateMachineFile(
        model: MachineModel,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        style: StubStyle,
        pkg: String?,
    ): String = buildString {
        pkg?.let { appendLine("package $it\n") }
        appendLine("import dev.gmvalentino.monaka.dsl.StateMachine")
        appendLine("import dev.gmvalentino.monaka.dsl.stateMachine\n")

        val initialRef = model.initial
            .takeIf { it.isNotEmpty() }
            ?.let { transitionRef(it, rootName, isSingle, model.name) }

        // Catch-all (root key) first, then other states in yaml order
        val catchAll = model.states.entries.filter { (k, _) -> isCatchAll(k, rootName, model.name) }
        val rest = model.states.entries.filter { (k, _) -> !isCatchAll(k, rootName, model.name) }
        val orderedStates = catchAll + rest

        when (style) {
            StubStyle.CLASS -> {
                appendLine("class ${model.name}StateMachine : StateMachine<$rootName, $actionName, $effectName> by stateMachine(")
                appendLine("    builder = {")
                initialRef?.let { appendLine("        initialState($it)") }
                orderedStates.forEachIndexed { index, (key, node) ->
                    if (index > 0 || initialRef != null) appendLine()
                    append(writeStateBlock(key, node, rootName, actionName, effectName, isSingle, model.name, "        "))
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
                    append(writeStateBlock(key, node, rootName, actionName, effectName, isSingle, model.name, "    "))
                }
                appendLine("}")
            }
        }
    }

    private fun writeStateBlock(
        key: String,
        node: dev.gmvalentino.monaka.gradle.model.StateNode,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        machineName: String,
        pad: String,
    ): String = buildString {
        val typeRef = stateTypeRef(key, rootName, machineName)
        appendLine("${pad}state<$typeRef> {")
        node.onEnter?.let { append(writeHookBlock("onEnter", it, rootName, actionName, effectName, isSingle, machineName, key, "$pad    ")) }
        node.onExit?.let { append(writeHookBlock("onExit", it, rootName, actionName, effectName, isSingle, machineName, key, "$pad    ")) }
        node.onUpdate?.let { append(writeHookBlock("onUpdate", it, rootName, actionName, effectName, isSingle, machineName, key, "$pad    ")) }
        for ((event, hook) in node.lifecycleHooks) {
            append(writeHookBlock(event, hook, rootName, actionName, effectName, isSingle, machineName, key, "$pad    "))
        }
        for ((action, handler) in node.on) {
            append(writeHandlerBlock(action, handler, rootName, actionName, effectName, isSingle, machineName, key, "$pad    "))
        }
        appendLine("${pad}}")
    }

    private fun writeHookBlock(
        hookName: String,
        hook: HookModel,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        machineName: String,
        sourcePath: String,
        pad: String,
    ): String = buildString {
        appendLine("${pad}$hookName {")
        hook.task?.let { append(writeTaskBlock(it, actionName, "$pad    ")) }
        hook.transitions.forEach { target ->
            appendLine("${pad}    transition(${stateTransitionExpr(sourcePath, target, rootName, isSingle, machineName)})")
        }
        hook.effects.forEach { appendLine("${pad}    sideEffect($effectName.$it)") }
        hook.dispatch?.let { appendLine("${pad}    dispatch($it)") }
        appendLine("${pad}}")
    }

    private fun writeHandlerBlock(
        action: String,
        handler: HandlerModel,
        rootName: String,
        actionName: String,
        effectName: String,
        isSingle: Boolean,
        machineName: String,
        sourcePath: String,
        pad: String,
    ): String = buildString {
        appendLine("${pad}on<$actionName.$action> {")
        if (handler.reject) {
            appendLine("$pad    reject()")
        } else {
            handler.task?.let { append(writeTaskBlock(it, actionName, "$pad    ")) }
            handler.transitions.forEach { target ->
                appendLine("$pad    transition(${stateTransitionExpr(sourcePath, target, rootName, isSingle, machineName)})")
            }
            handler.effects.forEach { appendLine("$pad    sideEffect($effectName.$it)") }
            handler.dispatch?.let { appendLine("$pad    dispatch($actionName.$it)") }
        }
        appendLine("$pad}")
    }

    private fun writeTaskBlock(task: TaskModel, actionName: String, pad: String): String = buildString {
        val args = buildList {
            task.key?.let { add("\"$it\"") }
            if (task.autoCancel) add("autoCancel = true")
        }.joinToString(", ")
        appendLine("${pad}task($args) {")
        task.dispatches.forEach { appendLine("$pad    dispatch($actionName.$it)") }
        appendLine("$pad}")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Reference to use in `state<...>` — catch-all uses bare root, leaf uses qualified path. */
    private fun stateTypeRef(key: String, rootName: String, machineName: String): String =
        if (isCatchAll(key, rootName, machineName)) rootName else "$rootName.$key"

    /** Reference to use in `transition(}` and `initialState()`. */
    private fun transitionRef(target: String, rootName: String, isSingle: Boolean, machineName: String): String =
        when (target) {
            rootName if isSingle -> "$rootName.$machineName"
            rootName -> rootName
            else -> "$rootName.$target"
        }

    /**
     * Produces the expression inside `transition(}` for a given source state path and target path.
     *
     * - Catch-all state (sourcePath == rootName): keeps constructor reference — `state.toXxx()` is
     *   not available on the sealed interface receiver.
     * - Self-transition (target == source): `state.toSelf()`
     * - Cross-state: `state.toXxx()` where the name is derived by stripping the common enclosing
     *   prefix between source and target, mirroring the processor's `buildFunctionName` algorithm.
     */
    private fun stateTransitionExpr(
        sourcePath: String,
        target: String,
        rootName: String,
        isSingle: Boolean,
        machineName: String,
    ): String {
        if (isCatchAll(sourcePath, rootName, machineName)) return transitionRef(target, rootName, isSingle, machineName)
        if (target == sourcePath) return "state.toSelf()"
        return "state.${toStateFunctionName(sourcePath, target)}()"
    }

    /**
     * Mirrors `TargetTransitionGenerator.buildFunctionName` using dot-path strings instead of KSP
     * class declarations.
     *
     * Examples (sourcePath → target → result):
     *   Loading          → Auth.SigningIn  → toAuthSigningIn
     *   Auth.SignedOut   → Auth.SigningIn  → toSigningIn
     *   Auth.SigningIn   → Auth.SignedOut  → toSignedOut
     *   Auth.SignedOut   → Loading         → toLoading
     */
    private fun toStateFunctionName(sourcePath: String, target: String): String {
        val sourceEnclosing = sourcePath.split(".").dropLast(1)
        val targetParts = target.split(".")
        val prefixLen = sourceEnclosing.zip(targetParts).takeWhile { (a, b) -> a == b }.size
        return "to" + targetParts.drop(prefixLen).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
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
            node.onUpdate?.let { addAll(it.effects) }
            node.lifecycleHooks.values.forEach { addAll(it.effects) }
            node.on.values.forEach { addAll(it.effects) }
        }
        return effects
    }

    private fun isCatchAll(key: String, rootName: String, machineName: String) =
        key == rootName || key == machineName

    companion object {
        private val HOOKS = setOf(
            "onEnter", "onExit", "onUpdate",
            "onResume", "onPause", "onStart", "onStop", "onCreate", "onDestroy",
        )
    }
}
