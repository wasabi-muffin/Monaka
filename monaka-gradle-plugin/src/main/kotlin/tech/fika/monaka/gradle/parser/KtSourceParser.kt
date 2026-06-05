package tech.fika.monaka.gradle.parser

import tech.fika.monaka.gradle.model.*
import java.io.File

/**
 * Text-based parser that extracts MachineModel instances from Kotlin source files.
 *
 * Uses regex + brace/paren balancing. Relies on the predictable structure of the
 * Monaka `stateMachine { }` DSL rather than a full Kotlin compiler / PSI dependency.
 *
 * Limitations:
 *  - Does not resolve type aliases or imports; references appear as written in source.
 *  - Guards/branches cannot be inferred statically and are not emitted.
 *  - String literals containing unbalanced braces would confuse block extraction
 *    (rare in DSL code; multi-line strings are handled up to triple-quote detection).
 */
class KtSourceParser {

    fun parseFiles(files: Iterable<File>): List<MachineModel> =
        files.filter { it.extension == "kt" }.flatMap { parseFile(it) }

    // ── File entry ────────────────────────────────────────────────────────────

    private fun parseFile(file: File): List<MachineModel> {
        val text = file.readText()
        return extractMachines(text, file.nameWithoutExtension)
    }

    // ── Machine extraction ────────────────────────────────────────────────────

    private fun extractMachines(text: String, fileHint: String): List<MachineModel> {
        val result = mutableListOf<MachineModel>()
        val seenBracePos = mutableSetOf<Int>()

        // Form 1: stateMachine<S, A, E> { ... }  (explicit type args, trailing lambda)
        for (match in stateMachineWithTypeArgsRegex.findAll(text)) {
            if (!seenBracePos.add(match.range.last)) continue
            val typeArgs = match.groupValues[1].split(",").map { it.trim() }
            val body = extractBlock(text, match.range.last) ?: continue
            val before = text.substring(maxOf(0, match.range.first - 300), match.range.first)
            result += parseMachineBody(
                body = body,
                name = inferName(before, fileHint),
                stateType = typeArgs.getOrElse(0) { "?" },
                actionType = typeArgs.getOrElse(1) { "?" },
            )
        }

        // Form 2: stateMachine(builder = { ... })  or  store(scope, builder = { ... })
        // Type parameters come from the class supertype declaration:
        //   : StateMachine<S, A, E> by stateMachine(...)
        //   : Store<S, A, E> by store(...)
        for (match in builderLambdaRegex.findAll(text)) {
            if (!seenBracePos.add(match.range.last)) continue
            val body = extractBlock(text, match.range.last) ?: continue
            val before = text.substring(maxOf(0, match.range.first - 600), match.range.first)
            val typeArgs = supertypeRegex.findAll(before).lastOrNull()
                ?.groupValues?.get(1)?.split(",")?.map { it.trim() }
                ?: continue
            result += parseMachineBody(
                body = body,
                name = inferName(before, fileHint),
                stateType = typeArgs.getOrElse(0) { "?" },
                actionType = typeArgs.getOrElse(1) { "?" },
            )
        }

        return result
    }

    private fun inferName(before: String, fileHint: String): String {
        val className = classNameRegex.findAll(before).lastOrNull()?.groupValues?.get(1)
        val propName = propertyNameRegex.findAll(before).lastOrNull()?.groupValues?.get(1)
        return (className ?: propName ?: fileHint)
            .removeSuffix("StateMachine")  // ClassName suffix: LoginStateMachine → Login
            .removeSuffix("Machine")       // property suffix: loginMachine → login
            .replaceFirstChar { it.uppercase() }
    }

    private fun parseMachineBody(
        body: String,
        name: String,
        stateType: String,
        actionType: String,
    ): MachineModel {
        // Strip constructor args from initialState(...) — data class fields stay in Kotlin
        val initial = firstSimpleArg(body, "initialState")
            ?.substringBefore("(")?.trim()
            ?.stripPrefix(stateType)
            ?.ifEmpty { stateType.substringAfterLast(".") }
            ?: ""

        val flatStates = mutableMapOf<String, StateNode>()
        for (match in stateBlockRegex.findAll(body)) {
            val rawType = match.groupValues[1].trim()
            // When state<RootType> itself is registered as a catch-all, path is "".
            // Use the root type simple name so it has a valid YAML key.
            val statePath = rawType.stripPrefix(stateType)
                .ifEmpty { stateType.substringAfterLast(".") }
            val stateBody = extractBlock(body, match.range.last) ?: continue
            flatStates[statePath] = parseStateBody(stateBody, stateType, actionType, statePath)
        }

        return MachineModel(
            name = name,
            initial = initial,
            states = buildHierarchy(flatStates),
        )
    }

    // ── State extraction ──────────────────────────────────────────────────────

    private fun parseStateBody(
        body: String,
        stateType: String,
        actionType: String,
        statePath: String,
    ): StateNode {
        val onEnter = findBlockByKeyword(body, "onEnter")
            ?.let { parseHookBody(it, stateType, statePath) }
        val onExit = findBlockByKeyword(body, "onExit")
            ?.let { parseHookBody(it, stateType, statePath) }

        val lifecycleHooks = mutableMapOf<String, HookModel>()
        for (event in LIFECYCLE_EVENTS) {
            val hookBody = findBlockByKeyword(body, event) ?: continue
            lifecycleHooks[event] = parseHookBody(hookBody, stateType, statePath) ?: HookModel()
        }

        val on = mutableMapOf<String, HandlerModel>()
        for (match in onBlockRegex.findAll(body)) {
            val rawAction = match.groupValues[1].trim()
            val actionName = rawAction.stripPrefix(actionType)
            val handlerBody = extractBlock(body, match.range.last) ?: continue
            on[actionName] = parseHandlerBody(handlerBody, stateType, actionType, statePath)
        }

        return StateNode(onEnter = onEnter, onExit = onExit, lifecycleHooks = lifecycleHooks, on = on)
    }

    // ── Hook (onEnter / onExit / lifecycle) ───────────────────────────────────

    private fun parseHookBody(body: String, stateType: String = "", statePath: String = ""): HookModel? {
        val task = findTaskBlock(body)
        val cancel = cancelRegex.find(body)?.groupValues?.get(1)
        val effects = collectSideEffects(body).map { it.extractEffectName() }

        val shallow = stripTaskBodies(body)

        // Direct transition { } call inside the hook (e.g. onEnter { transition { Loading } })
        val transMatch = transitionWithArgsRegex.find(shallow) ?: transitionNoArgsRegex.find(shallow)
        val transition = if (transMatch != null) {
            val transBody = extractBlock(body, transMatch.range.last)?.trim() ?: ""
            when {
                transBody.startsWith("state.") || transBody == "state" -> statePath.takeIf { it.isNotBlank() }
                else -> transBody.substringBefore("(").trim()
                    .stripPrefix(stateType)
                    .ifEmpty { stateType.substringAfterLast(".") }
                    .takeIf { it.isNotBlank() }
            }
        } else null

        val dispatchMatch = dispatchCallRegex.find(shallow)
        val dispatch = if (dispatchMatch != null)
            extractParen(body, dispatchMatch.range.last)?.trim()?.substringBefore("(")?.trim()
                ?.substringAfterLast(".")?.takeIf { it.isNotBlank() }
        else null

        return if (task != null || cancel != null || effects.isNotEmpty() || dispatch != null || transition != null)
            HookModel(task = task, effects = effects, cancel = cancel, dispatch = dispatch, transition = transition)
        else null
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    private fun parseHandlerBody(
        body: String,
        stateType: String,
        actionType: String,
        statePath: String,
    ): HandlerModel {
        val effects = mutableListOf<String>()

        // For dispatch search: blank-out task lambda bodies so we don't pick up
        // dispatch() calls that belong to the task, not the handler directly.
        val bodyNoTaskBodies = stripTaskBodies(body)

        // transition(E1, E2) { NewState }  or  transition { NewState }
        // Searched in original body — transition { } is always at handler level.
        var transition: String? = null
        val transWithArgs = transitionWithArgsRegex.find(body)
        val transNoArgs = transitionNoArgsRegex.find(body)
        val transMatch = transWithArgs ?: transNoArgs
        if (transMatch != null) {
            val transBody = extractBlock(body, transMatch.range.last)?.trim() ?: ""
            transition = when {
                // state.copy(...) or state.anything — the state stays the same type
                transBody.startsWith("state.") || transBody == "state" -> statePath
                else -> transBody.substringBefore("(").trim()
                    .stripPrefix(stateType)
                    .ifEmpty { stateType.substringAfterLast(".") }
                    .takeIf { it.isNotBlank() }
            }
            // Effects passed as value args to transition(E1, E2) { }
            transWithArgs?.groupValues?.get(1)
                ?.splitArgs()
                ?.filter { it.isNotBlank() }
                ?.map { it.extractEffectName() }
                ?.let { effects.addAll(it) }
        }

        effects.addAll(collectSideEffects(body).map { it.extractEffectName() })

        val reject = rejectRegex.containsMatchIn(body)

        var dispatch: String? = null
        val dispatchMatch = dispatchCallRegex.find(bodyNoTaskBodies)
        if (dispatchMatch != null) {
            dispatch = extractParen(body, dispatchMatch.range.last)
                ?.trim()?.substringBefore("(")?.trim()?.stripPrefix(actionType)
        }

        val cancel = cancelRegex.find(body)?.groupValues?.get(1)
        val task = findTaskBlock(body)

        return HandlerModel(
            transition = transition,
            effects = effects,
            reject = reject,
            dispatch = dispatch,
            cancel = cancel,
            task = task,
        )
    }

    // ── Task ──────────────────────────────────────────────────────────────────

    private fun findTaskBlock(text: String): TaskModel? {
        val match = taskCallRegex.find(text) ?: return null
        val rawArgs = match.groupValues[1]
        val args = rawArgs.splitArgs()

        // First positional arg or named `key = "..."` is the key
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
            val lifecycleHooks: MutableMap<String, HookModel> = mutableMapOf(),
            val on: MutableMap<String, HandlerModel> = mutableMapOf(),
            val states: MutableMap<String, Mutable> = mutableMapOf(),
        ) {
            fun freeze(): StateNode = StateNode(
                onEnter = onEnter,
                onExit = onExit,
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
                lifecycleHooks.putAll(node.lifecycleHooks)
                on.putAll(node.on)
            }
        }
        return root.mapValues { it.value.freeze() }
    }

    // ── Text utilities ────────────────────────────────────────────────────────

    /** Extract content between the `{` at [openBracePos] and its matching `}`. */
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
                inTriple -> { /* skip until closing """ handled above */ }
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

    /** Extract content between the `(` at [openParenPos] and its matching `)`. */
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

    /**
     * Returns a copy of [text] where the body content of every `task { }` lambda is
     * replaced with spaces. Positions are preserved, so match ranges remain valid against
     * the original [text]. Used to avoid picking up `dispatch()` calls inside task bodies.
     */
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
        val regex = Regex("""(?<!\w)$keyword\s*\{""")
        val match = regex.find(text) ?: return null
        return extractBlock(text, match.range.last)
    }

    /** Returns the raw argument text of the first `keyword(...)` call found. */
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

    /** `CounterEffect.ShowMessage("Counter reset!")` → `ShowMessage` */
    private fun String.extractEffectName(): String =
        substringBefore("(").trim().substringAfterLast(".").trim()

    /** Comma-split respecting nested parens and quoted strings. */
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

    private companion object {
        val propertyNameRegex = Regex("""(?:val|var)\s+(\w+)\s*[=:]""")
        val classNameRegex = Regex("""(?:class|object)\s+(\w+)""")
        // Form 1: stateMachine<S, A, E> { ... }
        val stateMachineWithTypeArgsRegex = Regex("""(?<!\w)stateMachine\s*<([^>]+)>\s*\{""")
        // Form 2: builder = { ... } inside stateMachine(...) or store(...)
        val builderLambdaRegex = Regex("""(?<!\w)builder\s*=\s*\{""")
        // Supertype declaration: StateMachine<S,A,E> or Store<S,A,E>
        val supertypeRegex = Regex("""(?:StateMachine|Store)\s*<([^>]+)>""")
        val stateBlockRegex = Regex("""(?<!\w)state\s*<([^>]+)>\s*\{""")
        val onBlockRegex = Regex("""(?<!\w)on\s*<([^>]+)>\s*\{""")
        val transitionWithArgsRegex = Regex("""(?<!\w)transition\s*\(([^)]*)\)\s*\{""")
        val transitionNoArgsRegex = Regex("""(?<!\w)transition\s*\{""")
        val sideEffectRegex = Regex("""(?<!\w)sideEffect\s*\(""")
        val rejectRegex = Regex("""(?<!\w)reject\s*\(\s*\)""")
        val dispatchCallRegex = Regex("""(?<!\w)dispatch\s*\(""")
        val cancelRegex = Regex("""(?<!\w)cancel\s*\(\s*"([^"]+)"\s*\)""")
        val taskCallRegex = Regex("""(?<!\w)task\s*(?:\(([^)]*)\))?\s*\{""")
        val LIFECYCLE_EVENTS = listOf("onResume", "onPause", "onStart", "onStop", "onCreate", "onDestroy")
    }
}
