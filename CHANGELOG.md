# Changelog

---

## 0.1.0

### Modules

- **`:monaka`** — Core KMP MVI state machine library targeting Android, iOS (arm64, simulatorArm64), JVM, macOS, watchOS, tvOS, Linux, JS, and Wasm.
- **`:monaka-test`** — Test DSL built on Turbine and `kotlinx-coroutines-test`. Provides `testStore { }` with exhaustive state / effect / handler-action assertions and virtual-time support.
- **`:monaka-compose`** — Compose Multiplatform helpers: `rememberStore`, `bindLifecycle`, `handleEffects`, `toViewStore`, `render`.
- **`:monaka-transitions`** — KSP annotation processor for generating sealed state/action transition helpers.
- **`:monaka-gradle-plugin`** — Gradle plugin with YAML, PlantUML, and stub code generators for state machines.

### Core features

- **Sequential action processing** — a single `Channel.UNLIMITED` + one coroutine guarantees deterministic, race-free state transitions regardless of concurrent `dispatch()` calls.
- **Typed DSL** — `store { }` and `stateMachine { }` builders with `state<T> { }`, `on<A> { }`, `onEnter`, `onExit`, `onUpdate`, `onResume`, `onPause`, and `onError` blocks.
- **Handler verbs** — `transition`, `sideEffect`, `reject`, `dispatch`, `task`, `task("key")`, `cancel`, and `guard` on every handler scope.
- **Keyed async tasks** — `task("key") { }` with explicit `cancel("key")` or `autoCancel = true` for automatic cancellation on state-type change.
- **State initializer** — optional `suspend () -> State` for async state restoration (e.g. from DataStore or a database) before the first action is processed.
- **Hierarchical state handling** — BFS supertype walk resolves handlers registered on parent sealed interfaces, so a catch-all `state<ParentState>` block handles actions unhandled by leaf states.
- **Plugin system** — synchronous observers with `onAction`, `onTransition`, `onEffect`, `onUnhandled`, `onRejected`, and `onError` hooks; plugins are isolated so one misbehaving plugin cannot stop others.
- **`LoggingPlugin`** — built-in reference implementation; configurable tag and logger.
- **`plugin { }` DSL** — ad-hoc typed plugins with optional type-filtered hooks (`onAction<T>`, `onEffect<T>`, `onTransition<T>`, `onError<T>`).
- **Relay / bridge** — `relay { state<S> { } / effect<E> { } / action<A> { } }` for inter-machine communication; `StoreRegistry` for coordinating multiple stores under one scope.
- **`StateMachineStore`** — base class for defining stores as named classes rather than factory lambdas; `name` defaults to the subclass's simple class name.
- **Lifecycle hooks** — `onResume`, `onPause`, `onStart`, `onStop`, `onCreate`, `onDestroy` per state; forwarded via `store.onLifecycleEvent(LifecycleEvent)`.
- **Binary compatibility validation** — ABI breakage is detected at build time via `binary-compatibility-validator`.
- **Explicit API mode** — all public declarations are explicitly marked.
