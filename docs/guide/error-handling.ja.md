# エラーハンドリング

デフォルトでは、ハンドラーまたはフック内でスローされた例外はランタイムによってキャッチされ、ステートは変更されず、インストール済みのすべてのプラグインが `Plugin.onError` で通知されます。`onError { }` フックは、エラーステートへの遷移、エフェクトの発行、またはリトライアクションのディスパッチといった対応を行う場所を提供します。

---

## `onError { }` フック

`onError` は `state<T>` ブロック内で宣言します。そのステートタイプに登録された**いずれかの**ハンドラーまたはフックが未処理の例外をスローしたときに発火します:

```kotlin
state<LoginState.Submitting> {
    onEnter {
        val username = loginRepository.login(state.username, state.password)
        transition(state.toAuthenticated(username = username))
        sideEffect(LoginEffect.NavigateToHome)
    }

    onError {
        transition(state.toError(message = error.message ?: "Unknown error"))
    }
}
```

`onError` ラムダは `ErrorScope` を暗黙のレシーバーとして実行されます:

| プロパティ | 型 | 説明 |
|---|---|---|
| `error` | `Throwable` | ハンドラーまたはフックがスローした生の例外。 |
| `handlerType` | `HandlerType<Action>` | エラーをスローしたハンドラーの種類。 |
| `state` | `SubState` | エラー発生時の現在のステート。 |

---

## スコープと継承

`onError` はアクションハンドラーと同じスーパータイプ BFS 解決に従います:

```kotlin
state<LoginState> {
    onError {
        transition(LoginState.Error(message = error.message ?: "Something went wrong"))
        sideEffect(LoginEffect.ShowError)
    }
}

state<LoginState.Submitting> {
    onError {
        transition(state.toError(message = error.message ?: "Login failed"))
    }
}
```

---

## リカバリーパターン

### エラーステートへ遷移

```kotlin
state<MyState.Loading> {
    onEnter {
        val data = repository.fetch(state.id)
        transition(MyState.Loaded(data))
    }

    onError {
        transition(MyState.Error(message = error.message ?: "Load failed"))
    }
}
```

### エフェクトのみ発行

```kotlin
state<MyState.Saving> {
    onError {
        sideEffect(MyEffect.ShowToast("Save failed — please try again"))
    }
}
```

### リトライアクションをディスパッチ

```kotlin
state<MyState.Uploading> {
    onError {
        if (state.retryCount < 3) {
            dispatch(MyAction.Retry(state.retryCount + 1))
        } else {
            transition(MyState.Error("Max retries exceeded"))
        }
    }
}
```

---

## `HandlerType`

| バリアント | 発生タイミング |
|---|---|
| `HandlerType.Action(action)` | `on<>` ハンドラーがスローした。`action` はディスパッチされたアクション。 |
| `HandlerType.Lifecycle(event)` | `onPause`・`onResume` などのフックがスローした。 |
| `HandlerType.Hook.Enter` | `onEnter` ブロックがスローした。 |
| `HandlerType.Hook.Exit` | `onExit` ブロックがスローした。 |
| `HandlerType.Hook.Update` | `onUpdate` ブロックがスローした。 |

---

## `onError` 自体がスローした場合

リカバリーフックが2度目の例外をスローすると、ランタイムはプラグインに `Plugin.onError` で通知し、以降のリカバリーを試みません。ステートは変化しません。

---

## プラグイン通知

`onError` ブロックが登録されていない場合（またはブロック自体がスローした場合）、すべてのインストール済みプラグインが `Plugin.onError(error, currentState, handlerType)` で通知されます:

```kotlin
class CrashReporterPlugin : Plugin<MyState, MyAction, MyEffect> {
    override fun onError(
        error: Throwable,
        currentState: MyState,
        handlerType: HandlerType<MyAction>,
    ) {
        crashReporter.recordException(error, mapOf(
            "state" to currentState::class.simpleName,
            "handler" to handlerType.toString(),
        ))
    }
}
```
