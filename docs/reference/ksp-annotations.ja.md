# KSPアノテーションリファレンス

`monaka-transitions`プロセッサーは、ソースリテンションアノテーションを2つ読み取り、そこからKotlin拡張関数を生成します。どちらも`dev.gmvalentino.monaka.core`に属しています。

---

## `@Transition`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Transition(vararg val to: KClass<out State> = [])
```

ステートの**クラスまたはデータクラス**に配置し、`to`の各ターゲットに対して1つの`toXxx()`拡張関数を生成します。

### パラメーター

| パラメーター | 説明 |
|---|---|
| `to` | 1つ以上のターゲットステート`KClass`値。ターゲットごとに1つの関数が生成されます。 |

### 生成される関数の命名

| レシーバー | ターゲット | 生成名 |
|---|---|---|
| `LoginState.Typing` | `LoginState.Submitting` | `toSubmitting()` |
| `Auth.SignedOut` | `Auth.SigningIn` | `toSigningIn()` |
| `Loading` | `Auth.SigningIn` | `toAuthSigningIn()` |

---

## `@SelfTransition`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SelfTransition
```

**シールドクラスまたはシールドインターフェース**に配置し、単一の`toSelf()`拡張関数を生成します。`toSelf()`は型の共有プロパティ（すべての直接シールドサブクラスに同名・同型で存在するプロパティ）を名前付きパラメーターとして受け取り、`when`ディスパッチで同じサブタイプの新しいインスタンスを返します。

### 共有プロパティ

```kotlin
@SelfTransition
sealed interface TimerState : State {
    val autoPause: Boolean

    data class Idle(override val autoPause: Boolean = false, …) : TimerState
    data class Running(override val autoPause: Boolean, …) : TimerState
}
```

生成:

```kotlin
fun TimerState.toSelf(autoPause: Boolean = this.autoPause): TimerState = when (this) {
    is TimerState.Idle    -> copy(autoPause = autoPause)
    is TimerState.Running -> copy(autoPause = autoPause)
}
```

---

## 謝辞

`@Transition` および `@SelfTransition` アノテーションと、その KSP プロセッサーは、sealed クラス階層間の
宣言的なコピーを行う KSP ライブラリ [Cream](https://github.com/TBSten/cream)（作者:
[TBSten](https://github.com/TBSten)）に着想を得ています。Cream は Apache-2.0 ライセンスで公開されています。
