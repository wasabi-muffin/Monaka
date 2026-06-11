# 階層状態

Monaka はシールドインターフェース階層をそのままサポートします。親（シールド）状態型にハンドラーブロックを登録することで、リーフサブタイプのいずれでも処理されないアクションをキャッチできます。

---

## キャッチオール親ブロック

```kotlin
state<LoginState> {
    on<LoginAction.Logout> {
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}

state<LoginState.Loading> {
    on<LoginAction.Cancel> { transition(LoginState.Idle) }
}
```

**ディスパッチ優先度:** ランタイムは BFS でスーパータイプを探索してハンドラーを解決します。最も具体的な登録型が優先されます。

---

## シールド階層の定義

```kotlin
sealed interface LoginState : State {
    data object Idle : LoginState
    data class Typing(val username: String, val password: String) : LoginState
    data class Submitting(val username: String, val password: String) : LoginState
    data class Authenticated(val user: User) : LoginState
    data class Error(val message: String) : LoginState
}
```

リーフ固有のブロックを先に登録し、その後にキャッチオールを登録します:

```kotlin
state<LoginState.Submitting> {
    onEnter {
        task("login", autoCancel = true) {
            val result = repo.login(state.username, state.password)
            dispatch(
                if (result is Success) LoginAction.LoginSucceeded(result.user)
                else LoginAction.LoginFailed(result.message)
            )
        }
    }
}

state<LoginState.Error> {
    on<LoginAction.Retry> { transition(LoginState.Idle) }
}

state<LoginState> {
    on<LoginAction.Logout> {
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}
```

---

## ネストされた階層

BFS スーパータイプ探索は任意の深さのシールド階層に対応します。例:

```
AuthState
├── AuthState.SignedOut
└── AuthState.SignedIn
    ├── AuthState.SignedIn.Active
    └── AuthState.SignedIn.Locked
```

`AuthState.SignedIn.Active` 状態でアクションがディスパッチされた場合の解決順序:

1. `state<AuthState.SignedIn.Active>` ブロック
2. `state<AuthState.SignedIn>` ブロック
3. `state<AuthState>` ブロック

---

## Gradle プラグインとの組み合わせ

[スタブジェネレーター](../gradle-plugin/stub-generator.md)は YAML スペックを読み込み、正しいシールド階層を自動生成します。ルートインターフェースに `@SelfTransition`、各リーフに `@Transition(…)` を配置し、KSP プロセッサーが `toXxx()` ヘルパー関数を生成します。
