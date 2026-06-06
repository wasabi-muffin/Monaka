package tech.fika.monaka.gradle

import org.junit.Test
import tech.fika.monaka.gradle.emit.YamlEmitter
import tech.fika.monaka.gradle.parser.KtSourceParser
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtSourceParserTest {

    private val parser = KtSourceParser()
    private val emitter = YamlEmitter()

    // ── Form 1: explicit type args on stateMachine<S,A,E> { } ────────────────

    @Test
    fun `parses explicit type args form`() {
        val src = """
            package com.example

            val counterMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
                initialState(CounterState.Idle)

                state<CounterState.Idle> {
                    on<CounterAction.Start> {
                        transition { CounterState.Running }
                        sideEffect(CounterEffect.PlaySound)
                    }
                    on<CounterAction.Reset> {
                        reject()
                    }
                }

                state<CounterState.Running> {
                    on<CounterAction.Stop> {
                        transition { CounterState.Idle }
                    }
                    on<CounterAction.Pause> {
                        transition { CounterState.Paused }
                        sideEffect(CounterEffect.ShowBanner, CounterEffect.Vibrate)
                    }
                }

                state<CounterState.Paused> {
                    on<CounterAction.Resume> {
                        transition { CounterState.Running }
                    }
                }

                install(LoggingPlugin())
            }
        """.trimIndent()

        val models = parser.parseFiles(listOf(writeTempFile("Counter.kt", src)))
        assertEquals(1, models.size)
        val m = models[0]

        assertEquals("Counter", m.name)           // "StateMachine" suffix stripped
        assertEquals("Idle", m.initial)

        val idle = m.states["Idle"]
        assertNotNull(idle)
        val start = idle.on["Start"]
        assertNotNull(start)
        assertEquals("Running", start.transition)
        assertEquals(listOf("PlaySound"), start.effects)   // bare effect name

        val reset = idle.on["Reset"]
        assertNotNull(reset)
        assertTrue(reset.reject)

        val running = m.states["Running"]
        val pause = running?.on?.get("Pause")
        assertEquals(listOf("ShowBanner", "Vibrate"), pause?.effects)  // bare names
    }

    // ── Form 2: builder = { } with supertype type args ────────────────────────

    @Test
    fun `parses builder lambda form with supertype type args`() {
        val src = """
            package com.example

            class LoginStateMachine(
                repo: LoginRepository,
            ) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {
                initialState(LoginState.Idle)

                state<LoginState.Idle> {
                    on<LoginAction.Submit> {
                        transition { LoginState.Submitting }
                    }
                }

                state<LoginState.Submitting> {
                    onEnter {
                        task("login") {
                            dispatch(LoginAction.LoginSucceeded)
                            dispatch(LoginAction.LoginFailed)
                        }
                    }
                    on<LoginAction.LoginSucceeded> {
                        transition { LoginState.Authenticated }
                    }
                    on<LoginAction.LoginFailed> {
                        transition { LoginState.Error }
                        sideEffect(LoginEffect.ShowError)
                    }
                }

                install(LoggingPlugin())
            })
        """.trimIndent()

        val models = parser.parseFiles(listOf(writeTempFile("Login.kt", src)))
        assertEquals(1, models.size)
        val m = models[0]

        assertEquals("Login", m.name)
        assertEquals("Idle", m.initial)

        val submitting = m.states["Submitting"]
        assertNotNull(submitting)
        val task = submitting.onEnter?.task
        assertNotNull(task)
        assertEquals("login", task.key)
        assertEquals(listOf("LoginAction.LoginSucceeded", "LoginAction.LoginFailed"), task.dispatches)

        assertEquals(listOf("ShowError"), submitting.on["LoginFailed"]?.effects)
        // No `on:` wrapper in YAML
        val yaml = emitter.emit(m)
        assertTrue(yaml.contains("LoginFailed:"))
        assertTrue(!yaml.lines().any { it.trim() == "on:" })
    }

    // ── state.copy → state name ───────────────────────────────────────────────

    @Test
    fun `replaces state-dot-copy transition with state path`() {
        val src = """
            val m = stateMachine<FeedState, FeedAction, FeedEffect> {
                initialState(FeedState.Active(""))
                state<FeedState.Active> {
                    on<FeedAction.Update> {
                        transition { state.copy(query = action.query) }
                    }
                }
            }
        """.trimIndent()

        val model = parser.parseFiles(listOf(writeTempFile("Feed.kt", src))).first()
        val transition = model.states["Active"]?.on?.get("Update")?.transition
        assertEquals("Active", transition)
    }

    // ── state.toXxx() generated transitions ──────────────────────────────────

    @Test
    fun `resolves state-dot-toXxx transition to target state name`() {
        val src = """
            val m = stateMachine<LoginState, LoginAction, LoginEffect> {
                initialState(LoginState.Idle)
                state<LoginState.Idle> {
                    on<LoginAction.Update> {
                        transition { state.toTyping(username = action.username, password = action.password) }
                    }
                }
                state<LoginState.Typing> {
                    on<LoginAction.Submit> {
                        transition { state.toSubmitting() }
                    }
                    on<LoginAction.Update> {
                        transition { state.copy(username = action.username) }
                    }
                }
                state<LoginState.Submitting> {
                    onEnter {
                        transition { state.toAuthenticated(username = result) }
                    }
                    onError {
                        transition { state.toError(message = error.message ?: "") }
                    }
                }
                state<LoginState.Error> {
                    on<LoginAction.Retry> {
                        transition { state.toSubmitting() }
                    }
                    on<LoginAction.Update> {
                        transition { state.toSelf(username = action.username) }
                    }
                }
            }
        """.trimIndent()

        val model = parser.parseFiles(listOf(writeTempFile("Login.kt", src))).first()

        // state.toXxx() resolves to target state
        assertEquals("Typing", model.states["Idle"]?.on?.get("Update")?.transition)
        assertEquals("Submitting", model.states["Typing"]?.on?.get("Submit")?.transition)
        assertEquals("Submitting", model.states["Error"]?.on?.get("Retry")?.transition)

        // state.copy() stays in same state
        assertEquals("Typing", model.states["Typing"]?.on?.get("Update")?.transition)

        // state.toSelf() stays in same state
        assertEquals("Error", model.states["Error"]?.on?.get("Update")?.transition)

        // onEnter hook transitions via state.toXxx()
        // (onError is a separate DSL hook, not captured in onEnter.transitions)
        assertEquals(listOf("Authenticated"), model.states["Submitting"]?.onEnter?.transitions)
    }

    @Test
    fun `resolves cross-hierarchy state-dot-toXxx to dot-path using state lookup`() {
        val src = """
            val m = stateMachine<AppState, AppAction, AppEffect> {
                initialState(AppState.Auth.SignedOut)

                state<AppState.Auth> {
                    on<AppAction.DoNothing> {}
                }
                state<AppState.Auth.SignedOut> {
                    on<AppAction.Attempt> {
                        transition { state.toAuthSigningIn(username = action.username, password = action.password) }
                    }
                }
                state<AppState.Auth.SigningIn> {
                    on<AppAction.Cancel> {
                        transition { state.toAuthSignedOut() }
                    }
                }
                state<AppState.Loading> {
                    on<AppAction.SignIn> {
                        transition { state.toAuthSigningIn(username = action.username, password = action.password) }
                    }
                    on<AppAction.Clear> {
                        transition { state.toLoading() }
                    }
                }
            }
        """.trimIndent()

        val model = parser.parseFiles(listOf(writeTempFile("App.kt", src))).first()

        // buildHierarchy nests Auth.SignedOut under states["Auth"].states["SignedOut"]
        val signedOut  = model.states["Auth"]?.states?.get("SignedOut")
        val signingIn  = model.states["Auth"]?.states?.get("SigningIn")
        val loading    = model.states["Loading"]

        // Cross-hierarchy: toAuthSigningIn → dot-path "Auth.SigningIn"
        assertEquals("Auth.SigningIn", signedOut?.on?.get("Attempt")?.transition)
        assertEquals("Auth.SigningIn", loading?.on?.get("SignIn")?.transition)

        // Same-level within Auth: toAuthSignedOut → dot-path "Auth.SignedOut"
        assertEquals("Auth.SignedOut", signingIn?.on?.get("Cancel")?.transition)

        // Flat self-reference: toLoading → "Loading"
        assertEquals("Loading", loading?.on?.get("Clear")?.transition)
    }

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    @Test
    fun `parses lifecycle hooks on state blocks`() {
        val src = """
            val m = stateMachine<FeedState, FeedAction, FeedEffect> {
                initialState(FeedState.Active)
                state<FeedState.Active> {
                    onResume {
                        dispatch(FeedAction.GoLive)
                    }
                    onPause {
                        cancel("poll")
                    }
                    on<FeedAction.GoLive> {
                        transition { FeedState.Active }
                    }
                }
            }
        """.trimIndent()

        val model = parser.parseFiles(listOf(writeTempFile("Feed.kt", src))).first()
        val active = model.states["Active"]
        assertNotNull(active)
        assertTrue(active.lifecycleHooks.containsKey("onResume"), "Expected onResume hook")
        assertTrue(active.lifecycleHooks.containsKey("onPause"), "Expected onPause hook")
        assertEquals("poll", active.lifecycleHooks["onPause"]?.cancel)
    }

    // ── Hierarchy ─────────────────────────────────────────────────────────────

    @Test
    fun `builds nested hierarchy from dot-path state types`() {
        val src = """
            val machine = stateMachine<CallState, CallAction, CallEffect> {
                initialState(CallState.Idle)
                state<CallState.Idle> {
                    on<CallAction.Dial> { transition { CallState.Active.Connecting } }
                }
                state<CallState.Active> {
                    on<CallAction.HangUp> {
                        transition { CallState.Ended }
                        sideEffect(CallEffect.ReleaseAudio)
                    }
                }
                state<CallState.Active.Connecting> {
                    on<CallAction.CallEstablished> {
                        transition { CallState.Active.Connected.Talking }
                    }
                }
                state<CallState.Active.Connected.Talking> {
                    on<CallAction.Hold> { transition { CallState.Active.Connected.OnHold } }
                }
                state<CallState.Ended> {}
            }
        """.trimIndent()

        val models = parser.parseFiles(listOf(writeTempFile("Call.kt", src)))
        val m = models[0]

        val active = m.states["Active"]
        assertNotNull(active, "Active should be a top-level state")
        assertTrue(active.on.containsKey("HangUp"))
        assertEquals(listOf("ReleaseAudio"), active.on["HangUp"]?.effects)

        val connecting = active.states["Connecting"]
        assertNotNull(connecting, "Connecting should be nested under Active")

        val talking = active.states["Connected"]?.states?.get("Talking")
        assertNotNull(talking, "Talking should be nested under Active.Connected")
        assertEquals("Active.Connected.OnHold", talking.on["Hold"]?.transition)
    }

    // ── YAML emission ─────────────────────────────────────────────────────────

    @Test
    fun `emits valid YAML for a simple machine`() {
        val src = """
            val toggleMachine = stateMachine<ToggleState, ToggleAction, ToggleEffect> {
                initialState(ToggleState.Off)
                state<ToggleState.Off> {
                    on<ToggleAction.TurnOn> {
                        transition { ToggleState.On }
                        sideEffect(ToggleEffect.Flash)
                    }
                }
                state<ToggleState.On> {
                    on<ToggleAction.TurnOff> {
                        transition { ToggleState.Off }
                    }
                }
            }
        """.trimIndent()

        val model = parser.parseFiles(listOf(writeTempFile("Toggle.kt", src))).first()
        val yaml = emitter.emit(model)

        assertTrue(yaml.contains("name: Toggle"))
        assertTrue(yaml.contains("initial: Off"))
        assertTrue(yaml.contains("transition: [On]"))
        assertTrue(yaml.contains("effect: [Flash]"))
        assertTrue(yaml.contains("TurnOff:"))
        assertTrue(!yaml.contains("package:"))
        assertTrue(!yaml.lines().any { it.trim() == "on:" })    // no `on:` wrapper
        println(yaml)
    }

    // ── dispatch in handlers ──────────────────────────────────────────────────

    @Test
    fun `emits dispatch in handler YAML`() {
        val src = """
            val m = stateMachine<AppState, AppAction, AppEffect> {
                initialState(AppState.Idle)
                state<AppState.Idle> {
                    on<AppAction.Start> {
                        dispatch(AppAction.Validate)
                        transition { AppState.Loading }
                    }
                    on<AppAction.Validate> {
                        transition { AppState.Valid }
                    }
                }
                state<AppState.Loading> {}
                state<AppState.Valid> {}
            }
        """.trimIndent()

        val model = parser.parseFiles(listOf(writeTempFile("App.kt", src))).first()
        val yaml = emitter.emit(model)
        println(yaml)

        assertTrue(yaml.contains("dispatch: [Validate]"), "Expected dispatch: [Validate] in:\n$yaml")
        assertTrue(yaml.contains("transition: [Loading]"))
    }

    // ── Real sample files ─────────────────────────────────────────────────────

    @Test
    fun `parses real LoginStateMachine and emits YAML`() {
        val sampleDir = File("../sample/shared/src/commonMain/kotlin/tech/fika/monaka/examples")
        if (!sampleDir.exists()) return

        val loginFile = sampleDir.resolve("login/LoginStateMachine.kt")
        if (!loginFile.exists()) return

        val models = parser.parseFiles(listOf(loginFile))
        assertTrue(models.isNotEmpty())
        val yaml = emitter.emit(models[0])
        println("=== LoginStateMachine YAML ===\n$yaml")
        assertTrue(yaml.contains("name: Login"))
        assertTrue(yaml.contains("initial: Idle"))
        assertTrue(!yaml.lines().any { it.trim() == "on:" })
    }

    @Test
    fun `parses real CounterStateMachine and emits YAML`() {
        val sampleDir = File("../sample/shared/src/commonMain/kotlin/tech/fika/monaka/examples")
        if (!sampleDir.exists()) return

        val counterFile = sampleDir.resolve("counter/CounterStateMachine.kt")
        if (!counterFile.exists()) return

        val models = parser.parseFiles(listOf(counterFile))
        assertTrue(models.isNotEmpty())
        val yaml = emitter.emit(models[0])
        println("=== CounterStateMachine YAML ===\n$yaml")
        assertTrue(yaml.contains("name: Counter"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun writeTempFile(name: String, content: String): File {
        val f = File.createTempFile(name.removeSuffix(".kt"), ".kt")
        f.writeText(content)
        f.deleteOnExit()
        return f
    }
}
