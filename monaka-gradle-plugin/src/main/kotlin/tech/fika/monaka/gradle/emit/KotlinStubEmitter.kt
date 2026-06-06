package tech.fika.monaka.gradle.emit

import tech.fika.monaka.gradle.StubStyle
import tech.fika.monaka.gradle.model.*

class KotlinStubEmitter {

    data class GeneratedFile(val name: String, val content: String)

    fun emit(
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
            GeneratedFile("$rootName.kt", emitStateFile(model, rootName, stateTree, isSingle, pkg, transitions)),
            GeneratedFile("$actionName.kt", emitSealedFile(actionName, "Action", actions, pkg)),
            GeneratedFile("$effectName.kt", emitSealedFile(effectName, "Effect", effects, pkg)),
            GeneratedFile("${model.name}StateMachine.kt",
                emitStateMachineFile(model, rootName, actionName, effectName, isSingle, style, pkg)),
        )
    }

    // ── Transition map ────────────────────────────────────────────────────────

    /**
     * Computes a map of state simple-name → list of distinct non-self transition targets
     * by walking every handler and hook in the model.
     */
    private fun computeTransitions(model: MachineModel): Map<String, List<String>> {
        val result = linkedMapOf<String, LinkedHashSet<String>>()

        fun addTarget(stateName: String, target: String?) {
            if (target != null && target != stateName) {
                result.getOrPut(stateName) { linkedSetOf() }.add(target.substringAfterLast("."))
            }
        }

        fun walk(states: Map<String, tech.fika.monaka.gradle.model.StateNode>) {
            for ((key, node) in states) {
                val name = key.substringAfterLast(".")
                node.on.values.forEach { handler -> addTarget(name, handler.transition) }
                node.onEnter?.transitions?.forEach { addTarget(name, it) }
                node.onExit?.transitions?.forEach { addTarget(name, it) }
                node.lifecycleHooks.values.forEach { h -> h.transitions.forEach { addTarget(name, it) } }
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
        transitions: Map<String, List<String>>,
    ): String = buildString {
        pkg?.let { appendLine("package $it\n") }
        if (transitions.isNotEmpty()) {
            appendLine("import tech.fika.monaka.core.SelfTransition")
            appendLine("import tech.fika.monaka.core.Transition")
        }
        appendLine("import tech.fika.monaka.core.State\n")
        if (transitions.isNotEmpty()) appendLine("@SelfTransition")
        appendLine("sealed interface $rootName : State {")
        if (isSingle) {
            appendLine("    data object ${model.name} : $rootName")
        } else {
            for ((_, child) in tree.children) {
                append(emitStateNode(child, rootName, "    ", transitions))
            }
        }
        appendLine("}")
    }

    private fun emitStateNode(
        node: StateNode,
        parentType: String,
        indent: String,
        transitions: Map<String, List<String>>,
    ): String = buildString {
        val targets = transitions[node.name]
        if (!targets.isNullOrEmpty()) {
            val args = targets.joinToString(", ") { "${it}::class" }
            appendLine("${indent}@Transition($args)")
        }
        if (node.isLeaf) {
            appendLine("${indent}data object ${node.name} : $parentType")
        } else {
            appendLine("${indent}sealed interface ${node.name} : $parentType {")
            for ((_, child) in node.children) {
                append(emitStateNode(child, node.name, "$indent    ", transitions))
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
        node.onEnter?.let { append(emitHookBlock("onEnter", it, rootName, actionName, effectName, isSingle, machineName, key, "$pad    ")) }
        node.onExit?.let { append(emitHookBlock("onExit", it, rootName, actionName, effectName, isSingle, machineName, key, "$pad    ")) }
        for ((event, hook) in node.lifecycleHooks) {
            append(emitHookBlock(event, hook, rootName, actionName, effectName, isSingle, machineName, key, "$pad    "))
        }
        for ((action, handler) in node.on) {
            append(emitHandlerBlock(action, handler, rootName, actionName, effectName, isSingle, machineName, key, "$pad    "))
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
        sourcePath: String,
        pad: String,
    ): String = buildString {
        appendLine("${pad}$hookName {")
        hook.task?.let { append(emitTaskBlock(it, actionName, "$pad    ")) }
        hook.transitions.forEach { target ->
            appendLine("${pad}    transition { ${stateTransitionExpr(sourcePath, target, rootName, isSingle, machineName)} }")
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
        sourcePath: String,
        pad: String,
    ): String = buildString {
        appendLine("${pad}on<$actionName.$action> {")
        if (handler.reject) {
            appendLine("${pad}    reject()")
        } else {
            handler.task?.let { append(emitTaskBlock(it, actionName, "$pad    ")) }
            handler.transition?.let { target ->
                appendLine("${pad}    transition { ${stateTransitionExpr(sourcePath, target, rootName, isSingle, machineName)} }")
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

    /**
     * Produces the expression inside `transition { }` for a given source state path and target path.
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
        if (sourcePath == rootName) return transitionRef(target, rootName, isSingle, machineName)
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
