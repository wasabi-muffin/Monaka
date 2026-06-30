@file:OptIn(org.jetbrains.kotlin.K1Deprecation::class)

package dev.gmvalentino.monaka.gradle.parser

import dev.gmvalentino.monaka.gradle.model.*
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import java.io.File
import java.lang.reflect.Proxy

/**
 * PSI-based parser that extracts [MachineModel] instances from Kotlin source files.
 *
 * Uses the Kotlin compiler's PSI tree for structural extraction, then delegates to
 * text-based methods for handler/hook content (transitions, effects, tasks, dispatch).
 *
 * Compared to a regex-based approach, this correctly handles:
 *  - `StateMachineBuilder<>` extension functions extracted to the same module
 *    (e.g. `private fun StateMachineBuilder<S,A,E>.handleIdle() { state<Idle> { … } }`)
 *  - Type argument extraction without regex edge cases
 *  - Lambda block boundaries regardless of string literal content
 *
 * **Classloader note**: this class must only be instantiated inside a plain
 * `URLClassLoader` (e.g. via [PsiParserBridge]). Gradle's
 * `InstrumentingVisitableURLClassLoader` cannot instrument the shaded IntelliJ
 * platform classes inside `kotlin-compiler-embeddable`, causing a
 * `NoClassDefFoundError` for `Disposable` at runtime.
 *
 * Known limitation: `StateBuilder<>` extension functions (handlers extracted out of a
 * `state<T> { }` block) are not resolved — only `StateMachineBuilder<>` extensions are
 * indexed.
 */
class PsiStateMachineParser {

    fun parseFiles(files: Iterable<File>): List<Pair<File, MachineModel>> {
        val ktSources = files.filter { it.extension == "kt" }
            .map { file -> file to psiFactory.createFile(file.name, file.readText()) }

        val extensionIndex = buildExtensionIndex(ktSources.map { it.second })

        return ktSources.flatMap { (file, ktFile) ->
            extractMachines(ktFile, extensionIndex).map { file to it }
        }
    }

    // ── PSI setup ─────────────────────────────────────────────────────────────

    private companion object {
        // Singleton per JVM — KotlinCoreEnvironment must not be created more than once.
        private val environment: KotlinCoreEnvironment by lazy {
            val config = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
            // Create a no-op Disposable via Proxy, avoiding a SAM-generated class in the
            // plugin JAR that statically references the shaded Disposable interface.
            // In this classloader context (a plain URLClassLoader) the shaded IntelliJ
            // classes are loadable, so the Proxy creation succeeds.
            val loader = KotlinCoreEnvironment::class.java.classLoader
            val disposableClass = loader.loadClass("org.jetbrains.kotlin.com.intellij.openapi.Disposable")
            val disposable = Proxy.newProxyInstance(loader, arrayOf(disposableClass)) { _, _, _ -> null }
            val companion = KotlinCoreEnvironment::class.java.getField("Companion").get(null)
            companion.javaClass
                .getDeclaredMethod(
                    "createForProduction",
                    disposableClass,
                    CompilerConfiguration::class.java,
                    EnvironmentConfigFiles::class.java,
                )
                .invoke(companion, disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES)
                as KotlinCoreEnvironment
        }
        private val psiFactory: KtPsiFactory by lazy {
            KtPsiFactory(environment.project, markGenerated = false)
        }
    }

    // ── Extension function index ──────────────────────────────────────────────

    /**
     * Index all top-level functions with a `StateMachineBuilder<…>` receiver across all
     * source files. Called once per [parseFiles] so the index covers every file in the
     * source set (enabling cross-file extension inlining).
     */
    private fun buildExtensionIndex(ktFiles: List<KtFile>): Map<String, KtNamedFunction> =
        ktFiles
            .flatMap { it.declarations.filterIsInstance<KtNamedFunction>() }
            .filter { fn -> "StateMachineBuilder" in (fn.receiverTypeReference?.text ?: "") }
            .mapNotNull { fn -> fn.name?.let { name -> name to fn } }
            .toMap()

    // ── Machine extraction ────────────────────────────────────────────────────

    private fun extractMachines(
        ktFile: KtFile,
        extensionIndex: Map<String, KtNamedFunction>,
    ): List<MachineModel> {
        val machines = mutableListOf<MachineModel>()
        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expr: KtCallExpression) {
                if (expr.calleeExpression?.text == "stateMachine") {
                    parseMachineCall(expr, ktFile, extensionIndex)?.let { machines += it }
                }
                super.visitCallExpression(expr)
            }
        })
        return machines
    }

    private fun parseMachineCall(
        expr: KtCallExpression,
        ktFile: KtFile,
        extensionIndex: Map<String, KtNamedFunction>,
    ): MachineModel? {
        val typeArgs: List<String>
        val lambda: KtLambdaExpression

        when {
            // Form 1: stateMachine<S, A, E> { }
            expr.typeArgumentList != null -> {
                typeArgs = expr.typeArgumentList!!.arguments
                    .map { it.typeReference?.text?.trim() ?: "?" }
                lambda = expr.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return null
            }
            // Form 2: stateMachine(builder = { })
            else -> {
                val builderArg = expr.valueArguments
                    .firstOrNull { it.getArgumentName()?.asName?.identifier == "builder" }
                    ?: return null
                lambda = builderArg.getArgumentExpression() as? KtLambdaExpression ?: return null
                typeArgs = findSupertypeArgs(expr) ?: return null
            }
        }

        val body = lambda.bodyExpression ?: return null
        val name = inferName(expr, ktFile)
        return parseMachineBody(body, typeArgs, name, extensionIndex)
    }

    private fun findSupertypeArgs(expr: KtCallExpression): List<String>? {
        var node = expr.parent
        while (node != null) {
            if (node is KtClass) {
                return node.superTypeListEntries
                    .firstOrNull { entry ->
                        val text = entry.typeReference?.text ?: ""
                        "StateMachine" in text || "Store" in text
                    }
                    ?.typeReference
                    ?.typeElement
                    ?.typeArgumentsAsTypes
                    ?.map { it.text.trim() }
            }
            node = node.parent
        }
        return null
    }

    private fun inferName(expr: KtCallExpression, ktFile: KtFile): String {
        var node = expr.parent
        while (node != null) {
            val raw = when (node) {
                is KtClass -> node.name
                is KtProperty -> node.name
                else -> null
            }
            if (raw != null) return raw
                .removeSuffix("StateMachine")
                .removeSuffix("Machine")
                .replaceFirstChar { it.uppercase() }
            node = node.parent
        }
        return ktFile.name.removeSuffix(".kt")
    }

    // ── Machine body ──────────────────────────────────────────────────────────

    private fun parseMachineBody(
        body: KtBlockExpression,
        typeArgs: List<String>,
        name: String,
        extensionIndex: Map<String, KtNamedFunction>,
    ): MachineModel {
        val stateType = typeArgs.getOrElse(0) { "?" }
        val actionType = typeArgs.getOrElse(1) { "?" }

        var initial = ""
        val stateBlocks = mutableListOf<Pair<String, String>>()

        forEachMachineStatement(body, extensionIndex) { stmt ->
            val call = stmt as? KtCallExpression ?: return@forEachMachineStatement
            when (call.calleeExpression?.text) {
                "initialState" -> {
                    val arg = call.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: ""
                    initial = arg.substringBefore("(").trim()
                        .stripPrefix(stateType)
                        .ifEmpty { stateType.substringAfterLast(".") }
                }
                "state" -> {
                    val rawType = call.typeArgumentList?.arguments
                        ?.firstOrNull()?.typeReference?.text?.trim()
                        ?: return@forEachMachineStatement
                    val stateBodyText = call.lambdaArguments
                        .firstOrNull()?.getLambdaExpression()?.bodyExpression?.text
                        ?: return@forEachMachineStatement
                    stateBlocks += rawType to stateBodyText
                }
            }
        }

        val statePathLookup: Map<String, String> = stateBlocks.mapNotNull { (rawType, _) ->
            val path = rawType.stripPrefix(stateType).ifEmpty { stateType.substringAfterLast(".") }
            if (path.isBlank()) return@mapNotNull null
            path.split(".").joinToString("") to path
        }.toMap()

        val flatStates = mutableMapOf<String, StateNode>()
        for ((rawType, stateBodyText) in stateBlocks) {
            val statePath = rawType.stripPrefix(stateType)
                .ifEmpty { stateType.substringAfterLast(".") }
            flatStates[statePath] = parseStateBody(stateBodyText, stateType, actionType, statePath, statePathLookup)
        }

        return MachineModel(name = name, initial = initial, states = buildHierarchy(flatStates))
    }

    /**
     * Iterate over the direct statements of [block], recursively inlining the bodies of
     * any calls to known `StateMachineBuilder` extension functions from [extensionIndex].
     */
    private fun forEachMachineStatement(
        block: KtBlockExpression,
        extensionIndex: Map<String, KtNamedFunction>,
        visitor: (KtExpression) -> Unit,
    ) {
        for (stmt in block.statements) {
            val callName = (stmt as? KtCallExpression)?.calleeExpression?.text
            val extensionFn = if (callName != null) extensionIndex[callName] else null
            if (extensionFn != null) {
                val fnBody = extensionFn.bodyBlockExpression ?: continue
                forEachMachineStatement(fnBody, extensionIndex, visitor)
            } else {
                visitor(stmt)
            }
        }
    }

    // ── State / handler / hook extraction (text-based) ────────────────────────

    private fun parseStateBody(
        body: String,
        stateType: String,
        actionType: String,
        statePath: String,
        statePathLookup: Map<String, String> = emptyMap(),
    ): StateNode {
        val onEnter = findBlockByKeyword(body, "onEnter")
            ?.let { parseHookBody(it, stateType, statePath, statePathLookup) }
        val onExit = findBlockByKeyword(body, "onExit")
            ?.let { parseHookBody(it, stateType, statePath, statePathLookup) }
        val onUpdate = findBlockByKeyword(body, "onUpdate")
            ?.let { parseHookBody(it, stateType, statePath, statePathLookup) }

        val lifecycleHooks = mutableMapOf<String, HookModel>()
        for (event in LIFECYCLE_EVENTS) {
            val hookBody = findBlockByKeyword(body, event) ?: continue
            lifecycleHooks[event] = parseHookBody(hookBody, stateType, statePath, statePathLookup) ?: HookModel()
        }

        val on = mutableMapOf<String, HandlerModel>()
        for (match in onBlockRegex.findAll(body)) {
            val rawAction = match.groupValues[1].trim()
            val actionName = rawAction.stripPrefix(actionType)
            val handlerBody = extractBlock(body, match.range.last) ?: continue
            on[actionName] = parseHandlerBody(handlerBody, stateType, actionType, statePath, statePathLookup)
        }

        return StateNode(onEnter = onEnter, onExit = onExit, onUpdate = onUpdate, lifecycleHooks = lifecycleHooks, on = on)
    }

    private fun parseHookBody(
        body: String,
        stateType: String = "",
        statePath: String = "",
        statePathLookup: Map<String, String> = emptyMap(),
    ): HookModel? {
        val task = findTaskBlock(body)
        val cancel = cancelRegex.find(body)?.groupValues?.get(1)
        val effects = collectSideEffects(body).map { it.extractEffectName() }
        val shallow = stripTaskBodies(body)

        val transitions = transitionCallRegex.findAll(shallow).toList()
            .sortedBy { it.range.first }
            .distinctBy { it.range.first }
            .mapNotNull { transMatch ->
                val transBody = extractParen(body, transMatch.range.last)?.trim() ?: ""
                when {
                    transBody.startsWith("state.to") && !transBody.startsWith("state.toSelf") -> {
                        val suffix = transBody.removePrefix("state.to").substringBefore("(").trim()
                        (statePathLookup[suffix] ?: suffix).takeIf { it.isNotBlank() }
                    }
                    transBody.startsWith("state.") || transBody == "state" -> statePath.takeIf { it.isNotBlank() }
                    else -> transBody.substringBefore("(").trim()
                        .stripPrefix(stateType)
                        .ifEmpty { stateType.substringAfterLast(".") }
                        .takeIf { it.isNotBlank() }
                }
            }
            .distinct()

        val dispatches = dispatchCallRegex.findAll(shallow).mapNotNull { dm ->
            extractParen(body, dm.range.last)?.trim()?.substringBefore("(")?.trim()
                ?.substringAfterLast(".")?.takeIf { it.isNotBlank() }
        }.distinct().toList()

        return if (task != null || cancel != null || effects.isNotEmpty() || dispatches.isNotEmpty() || transitions.isNotEmpty())
            HookModel(task = task, effects = effects, cancel = cancel, dispatches = dispatches, transitions = transitions)
        else null
    }

    private fun parseHandlerBody(
        body: String,
        stateType: String,
        actionType: String,
        statePath: String,
        statePathLookup: Map<String, String> = emptyMap(),
    ): HandlerModel {
        val effects = mutableListOf<String>()
        val bodyNoTaskBodies = stripTaskBodies(body)

        val transitions = transitionCallRegex.findAll(bodyNoTaskBodies).toList()
            .sortedBy { it.range.first }
            .mapNotNull { transMatch ->
                val transBody = extractParen(body, transMatch.range.last)?.trim() ?: ""
                when {
                    transBody.startsWith("state.to") && !transBody.startsWith("state.toSelf") -> {
                        val suffix = transBody.removePrefix("state.to").substringBefore("(").trim()
                        (statePathLookup[suffix] ?: suffix).takeIf { it.isNotBlank() } ?: statePath
                    }
                    transBody.startsWith("state.") || transBody == "state" -> statePath
                    else -> transBody.substringBefore("(").trim()
                        .stripPrefix(stateType)
                        .ifEmpty { stateType.substringAfterLast(".") }
                        .takeIf { it.isNotBlank() }
                }
            }
            .distinct()

        effects.addAll(collectSideEffects(body).map { it.extractEffectName() })

        val reject = rejectRegex.containsMatchIn(body)

        val dispatches = dispatchCallRegex.findAll(bodyNoTaskBodies).mapNotNull { dm ->
            extractParen(body, dm.range.last)
                ?.trim()?.substringBefore("(")?.trim()?.stripPrefix(actionType)
                ?.takeIf { it.isNotBlank() }
        }.distinct().toList()

        val cancel = cancelRegex.find(body)?.groupValues?.get(1)
        val task = findTaskBlock(body)

        return HandlerModel(
            transitions = transitions,
            effects = effects,
            reject = reject,
            dispatches = dispatches,
            cancel = cancel,
            task = task,
        )
    }

    private fun findTaskBlock(text: String): TaskModel? {
        val match = taskCallRegex.find(text) ?: return null
        val rawArgs = match.groupValues[1]
        val args = rawArgs.splitArgs()

        val key = args.firstOrNull { !it.contains("=") }?.removeSurrounding("\"")
            ?: args.firstOrNull { it.trimStart().startsWith("key") }
                ?.substringAfter("=")?.trim()?.removeSurrounding("\"")

        val autoCancel = args.any { it.contains("autoCancel") && it.contains("true") }

        val taskBody = extractBlock(text, match.range.last) ?: return TaskModel(key, autoCancel)
        val dispatches = dispatchCallRegex.findAll(taskBody).mapNotNull { dm ->
            extractParen(taskBody, dm.range.last)?.trim()?.substringBefore("(")?.trim()
        }.filter { it.isNotBlank() }.toList()

        return TaskModel(key = key, autoCancel = autoCancel, dispatches = dispatches)
    }

    // ── Hierarchy ─────────────────────────────────────────────────────────────

    private fun buildHierarchy(flat: Map<String, StateNode>): Map<String, StateNode> {
        data class Mutable(
            var onEnter: HookModel? = null,
            var onExit: HookModel? = null,
            var onUpdate: HookModel? = null,
            val lifecycleHooks: MutableMap<String, HookModel> = mutableMapOf(),
            val on: MutableMap<String, HandlerModel> = mutableMapOf(),
            val states: MutableMap<String, Mutable> = mutableMapOf(),
        ) {
            fun freeze(): StateNode = StateNode(
                onEnter = onEnter,
                onExit = onExit,
                onUpdate = onUpdate,
                lifecycleHooks = lifecycleHooks,
                on = on,
                states = states.mapValues { it.value.freeze() },
            )
        }

        val root = mutableMapOf<String, Mutable>()
        for ((path, node) in flat.entries.sortedBy { it.key.count { c -> c == '.' } }) {
            val parts = path.split(".")
            var current = root
            for (part in parts.dropLast(1)) {
                current = current.getOrPut(part) { Mutable() }.states
            }
            with(current.getOrPut(parts.last()) { Mutable() }) {
                onEnter = node.onEnter
                onExit = node.onExit
                onUpdate = node.onUpdate
                lifecycleHooks.putAll(node.lifecycleHooks)
                on.putAll(node.on)
            }
        }
        return root.mapValues { it.value.freeze() }
    }

    // ── Text utilities ────────────────────────────────────────────────────────

    private fun extractBlock(text: String, openBracePos: Int): String? {
        var depth = 0
        var i = openBracePos + 1
        val start = i
        var inDouble = false
        var inTriple = false
        while (i < text.length) {
            when {
                !inDouble && !inTriple && i + 2 < text.length && text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    inTriple = !inTriple; i += 3; continue
                }
                inTriple -> {}
                !inTriple && text[i] == '"' && (i == 0 || text[i - 1] != '\\') -> inDouble = !inDouble
                !inDouble && text[i] == '{' -> depth++
                !inDouble && text[i] == '}' -> {
                    if (depth == 0) return text.substring(start, i)
                    depth--
                }
            }
            i++
        }
        return null
    }

    private fun extractParen(text: String, openParenPos: Int): String? {
        var depth = 0
        var i = openParenPos + 1
        val start = i
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    if (depth == 0) return text.substring(start, i)
                    depth--
                }
            }
            i++
        }
        return null
    }

    private fun stripTaskBodies(text: String): String {
        if (!text.contains("task")) return text
        val sb = StringBuilder(text)
        for (match in taskCallRegex.findAll(text)) {
            val bracePos = match.range.last
            val content = extractBlock(text, bracePos) ?: continue
            val start = bracePos + 1
            val end = start + content.length
            for (i in start until minOf(end, sb.length)) sb[i] = ' '
        }
        return sb.toString()
    }

    private fun findBlockByKeyword(text: String, keyword: String): String? {
        val match = Regex("""(?<!\w)$keyword\s*\{""").find(text) ?: return null
        return extractBlock(text, match.range.last)
    }

    private fun firstSimpleArg(text: String, keyword: String): String? {
        val match = Regex("""(?<!\w)$keyword\s*\(""").find(text) ?: return null
        return extractParen(text, match.range.last)?.trim()
    }

    private fun collectSideEffects(body: String): List<String> {
        val effects = mutableListOf<String>()
        for (match in sideEffectRegex.findAll(body)) {
            val argsText = extractParen(body, match.range.last) ?: continue
            argsText.splitArgs().filter { it.isNotBlank() }.let { effects.addAll(it) }
        }
        return effects
    }

    private fun String.extractEffectName(): String =
        substringBefore("(").trim().substringAfterLast(".").trim()

    private fun String.splitArgs(): List<String> {
        val args = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var inStr = false
        for (ch in this) {
            when {
                ch == '"' -> { inStr = !inStr; cur.append(ch) }
                inStr -> cur.append(ch)
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth--; cur.append(ch) }
                ch == ',' && depth == 0 -> { args += cur.toString().trim(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotBlank()) args += cur.toString().trim()
        return args
    }

    private fun String.stripPrefix(prefix: String): String {
        if (prefix.isBlank()) return this
        val dot = "$prefix."
        return when {
            startsWith(dot) -> substring(dot.length)
            this == prefix -> ""
            else -> this
        }
    }

    // ── Regex constants ───────────────────────────────────────────────────────

    private val onBlockRegex = Regex("""(?<!\w)on\s*<([^>]+)>\s*\{""")
    private val transitionCallRegex = Regex("""(?<!\w)transition\s*\(""")
    private val sideEffectRegex = Regex("""(?<!\w)sideEffect\s*\(""")
    private val rejectRegex = Regex("""(?<!\w)reject\s*\(\s*\)""")
    private val dispatchCallRegex = Regex("""(?<!\w)dispatch\s*\(""")
    private val cancelRegex = Regex("""(?<!\w)cancel\s*\(\s*"([^"]+)"\s*\)""")
    private val taskCallRegex = Regex("""(?<!\w)task\s*(?:\(([^)]*)\))?\s*\{""")
    private val LIFECYCLE_EVENTS = listOf("onResume", "onPause", "onStart", "onStop", "onCreate", "onDestroy")
}
