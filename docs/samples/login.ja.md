# ログイン

**ソース:** `sample/shared/…/examples/login/`

ログインサンプルは、`onEnter` ブロッキングパターン・`onError` リカバリーフック・`@Transition` と `@SelfTransition` から生成されたトランジションヘルパーを示すマルチステートマシンです。

---

## 型

```kotlin
@SelfTransition
sealed interface LoginState : State {
    data object Idle : LoginState

    @Transition(Submitting::class)
    data class Typing(val username: String, val password: String) : LoginState {
        val isValid get() = username.isNotBlank() && password.isNotBlank()
    }

    @Transition(Authenticated::class, Error::class)
    data class Submitting(val username: String, val password: String) : LoginState

    data class Authenticated(val username: String) : LoginState
    data class Error(val message: String, val username: String, val password: String) : LoginState
}

sealed interface LoginAction : Action {
    data class UpdateCredentials(val username: String, val password: String) : LoginAction
    data object Submit : LoginAction
    data object Retry  : LoginAction
    data object Logout : LoginAction
}
```

---

## デモされるパターン

### `onEnter` ブロッキングパターン

`Submitting.onEnter` はリポジトリをサスペンド関数として直接呼び出します。コルーチンがサスペンドしている間、アクションキューは停止します。

### `onError` リカバリー

`loginRepository.login()` がスローした場合、`onError` ブロックがそれをキャッチし `Error` ステートに遷移します。

### 生成されたトランジションヘルパー

`Typing` と `Submitting` の `@Transition` により、KSP プロセッサーが型付きの `toXxx()` 関数を生成します:

```kotlin
fun LoginState.Typing.toSubmitting(): LoginState.Submitting =
    LoginState.Submitting(username = this.username, password = this.password)
```

### ログアウト用のキャッチオール親ブロック

`Logout` はすべてのサブステートから有効です。`state<LoginState>`（シールドルート）に登録することで、現在どのサブタイプにいても関係なくハンドラーが発火します。
