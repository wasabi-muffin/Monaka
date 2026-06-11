# Compose & マルチプラットフォーム連携

このページで説明するヘルパーは `monaka-compose` アーティファクトによって提供されます：

```kotlin
implementation("dev.gmvalentino.monaka:monaka-compose:<version>")
```

---

## `rememberStore` — コンポジションスコープストア

```kotlin
@Composable
fun CounterScreen() {
    val store = rememberStore { scope ->
        CounterStateMachine(scope, counterRepository)
    }
}
```

---

## `toViewStore` — ライフサイクル対応ステート収集

```kotlin
@Composable
fun CounterScreen(store: Store<CounterState, CounterAction, CounterEffect>) {
    val viewStore = store.toViewStore()

    Text("Count: ${viewStore.state.count}")
    Button(onClick = { viewStore.dispatch(CounterAction.Increment) }) {
        Text("Increment")
    }
}
```

---

## `handleEffects` — ライフサイクル対応エフェクト収集

```kotlin
@Composable
fun LoginScreen(store: Store<LoginState, LoginAction, LoginEffect>, navController: NavController) {
    store.handleEffects { effect ->
        when (effect) {
            LoginEffect.NavigateToHome  -> navController.navigate("home")
            LoginEffect.NavigateToLogin -> navController.navigate("login")
            is LoginEffect.ShowError    -> showSnackbar(effect.message)
        }
    }

    val viewStore = store.toViewStore()
}
```

---

## `bindLifecycle` — 自動ライフサイクル転送

```kotlin
@Composable
fun TimerScreen(store: Store<TimerState, TimerAction, TimerEffect>) {
    store.bindLifecycle()
}
```

---

## `render` — インラインステートレンダリング

```kotlin
viewStore.state.render<LoginState.Authenticated> {
    Text("Welcome, ${renderState.username}!")
}

viewStore.state.render<LoginState.Error> {
    Text("Error: ${renderState.message}", color = Color.Red)
}
```
