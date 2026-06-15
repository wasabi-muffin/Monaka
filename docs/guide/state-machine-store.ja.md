# StateMachineStore と再利用可能な設定

Monaka では、大規模なマシンを構造化するパターンとして **`StateMachineStore`**（DSL をインラインで持つ名前付きクラス）と **`stateMachine { }`**（再利用可能な設定値）の2つを提供しています。どちらも同じランタイム動作を生成しますが、違いはコードの整理方法にあります。

---

## ストア名

すべてのストアは `name: String` プロパティを持ちます。ビルダーで `name(…)` を使って設定します:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    name("Login")
    initialState(MyState.Idle)
}
println(store.name)  // "Login"
```

`StateMachineStore` のサブクラスや `StateMachine` を委譲した名前付きクラスでは、明示的に設定されていない場合、`name` はサブクラスのシンプルクラス名に自動的にデフォルト設定されます:

```kotlin
class LoginStateMachine(…) : StateMachine<…> by stateMachine(builder = { … })
// store(LoginStateMachine(…), scope).name == "LoginStateMachine"
```

これにより、ストアごとのログ出力にタグを付けたいグローバル `StoreRegistry` プラグインで特に役立ちます:

```kotlin
StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }
}
```

---

## `store { }` — インライン匿名マシン

```kotlin
val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
    initialState(CounterState(0))
    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
    }
}
```

---

## `stateMachine { }` — 再利用可能な設定

`stateMachine { }` は、起動せずにイミュータブルな `StateMachine` のスナップショットを構築します。

```kotlin
val loginMachineConfig = stateMachine<LoginState, LoginAction, LoginEffect> {
    initialState(LoginState.Idle)
    state<LoginState.Idle> { … }
    state<LoginState.Typing> { … }
}

val store1 = store(loginMachineConfig, scope1)
val store2 = store(loginMachineConfig, scope2, initialState = LoginState.Typing("bob"))
```

---

## 非同期による状態の復元

```kotlin
val store = store(
    stateMachine = loginMachineConfig,
    scope = viewModelScope,
    initializer = { dataStore.data.first().toLoginState() },
) {
    initialState(LoginState.Idle)
}
```

---

## `StateMachine` の委譲 — 依存関係を注入する名前付きクラス

```kotlin
class LoginStateMachine(
    loginRepository: LoginRepository,
) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {
    initialState(LoginState.Idle)

    state<LoginState.Typing> {
        on<LoginAction.Submit> {
            task("login", autoCancel = true) {
                when (val result = loginRepository.login(state.username, state.password)) {
                    is Success -> dispatch(LoginAction.LoginSucceeded(result.user))
                    is Failure -> dispatch(LoginAction.LoginFailed(result.message))
                }
            }
            transition(LoginState.Submitting)
        }
    }

    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.NavigateToLogin)
        }
    }

    install(LoggingPlugin(tag = "Login"))
})
```

---

## パターンの選択

| パターン | 適用場面 |
|---|---|
| `store { }` インライン | 小規模マシン、テスト不要、または ViewModel 内にマシンが完結する場合。 |
| `stateMachine { }` + `store(config, scope)` | 再利用可能な設定値が必要な場合 — 複数インスタンス、`testStore` への受け渡し、設定と起動の分離。 |
| 名前付きクラス `: StateMachine<> by stateMachine { }` | 依存関係を注入する必要があり、`testStore` にも直接渡せる名前付き型クラスが必要な場合。 |
