package dev.gmvalentino.monaka.runtime

import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.InternalMonakaApi
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.plugin.Plugin
import dev.gmvalentino.monaka.relay.Relay
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope

/**
 * A keyed collection of [Store] instances that automatically applies [Relay]s
 * as stores are registered.
 *
 * Multiple instances of the same [Store] subclass can be registered simultaneously,
 * each identified by its unique [Store.id]. Stores are deregistered individually
 * via [unregister], typically when they are disposed.
 *
 * The registry serves three purposes:
 * 1. **Keying** — stores are stored under their [KClass] so they can be retrieved
 *    without holding individual references.
 * 2. **Relaying** — [Relay]s installed via [bind] start observing a
 *    source store as soon as a matching instance is registered. Relay targets are resolved
 *    lazily through the registry at emission time, so a relay reaches whatever target stores
 *    are registered when an event fires. All relay coroutines run in [bridgeScope].
 * 3. **Global plugins** — [Plugin]s installed via [install] are attached to every store
 *    currently in the registry and to every store registered in the future.
 *
 * ### Typical usage
 * ```kotlin
 * val registry = StoreRegistry(viewModelScope)
 *
 * registry.bind(
 *     relay(from = AuthStore::class) {
 *         state<AuthState.SignedIn>  { dispatch(CartStore::class, CartAction.LoadForUser(event.user.id)) }
 *         state<AuthState.SignedOut> { dispatch(CartStore::class, CartAction.Clear) }
 *     }
 * )
 *
 * AuthStore(authMachine, scope).register(registry)
 * CartStore(cartMachine, scope).register(registry)
 * ```
 *
 * Relays and stores may be installed in any order. When the source store of a relay is
 * already registered at install time, the relay starts observing it immediately.
 *
 * ### Store lifetime and cleanup
 * Registered stores are **automatically** stopped and unregistered when the store's owning
 * [CoroutineScope] is cancelled (e.g. `viewModelScope` cleared on Android). [register] attaches
 * an [Store.invokeOnCompletion] callback that calls [Store.stop] and [unregister] when the scope
 * completes.
 *
 * Calling [Store.stop] directly does **not** trigger [Store.invokeOnCompletion] — it only cancels
 * the store's internal processing coroutine. To unregister when using explicit `stop()` (e.g. from
 * a Compose `DisposableEffect`), call [unregister] manually after `stop()`:
 * ```kotlin
 * DisposableEffect(viewModel) {
 *     onDispose {
 *         viewModel.store.stop()
 *         registry.unregister(viewModel.store)
 *     }
 * }
 * ```
 *
 * To unregister without stopping the store — for example, to move a store between registries —
 * call [unregister] directly:
 * ```kotlin
 * registry.unregister(store)
 * ```
 *
 * ### Threading
 * [StoreRegistry] is **not thread-safe**. All calls to [register], [unregister], [bind],
 * [install], and [get]/[getAll] must be made from the same thread — typically the main thread
 * on Android and iOS. Pass a [bridgeScope] confined to that same thread (e.g. `viewModelScope`, which
 * runs on `Dispatchers.Main`) so relay collector coroutines also access the registry on the
 * main thread. Violating this contract can cause lost updates or `ConcurrentModificationException`.
 *
 * ### Known limitations
 * Relay collector coroutines are keyed by their *source* store. When a target store is
 * unregistered, any relay that was dispatching to it keeps collecting from the source and
 * simply dispatches into an empty result — a no-op per emission. Jobs are only canceled
 * when the source store itself is unregistered.
 *
 * @param bridgeScope The scope used for all relay coroutines. Must be confined to the same
 *                    thread used to call [register] and [unregister] (typically `Dispatchers.Main`).
 *                    Pass the same scope that owns the stores so all relay work is canceled together.
 */
@OptIn(InternalMonakaApi::class)
public class StoreRegistry(
    private val bridgeScope: CoroutineScope = MainScope(),
    initializer: StoreRegistry.() -> Unit = {},
) {
    /**
     * Receiver scope passed to the plugin factory lambda in [install].
     *
     * @property store The store the plugin is being attached to.
     * @property name  The best available human-readable name for the store: [Store.name] if
     *   explicitly set, otherwise [store]'s class simple name, otherwise [Store.id].
     *   Use this rather than [store].[Store.name] directly to get automatic fallback.
     */
    public class PluginScope(public val store: Store<*, *, *>) {
        /** The store's explicit name if set, otherwise its class simple name, otherwise its id. */
        public val name: String = store.name.ifEmpty { store::class.simpleName ?: store.id }
    }

    private val stores = LinkedHashMap<KClass<out Store<*, *, *>>, MutableList<Store<*, *, *>>>()
    private val relays = mutableListOf<Relay<*, *, *>>()
    private val relayJobs = HashMap<String, MutableList<Job>>()
    private val globalPlugins = mutableListOf<PluginScope.() -> Plugin>()

    init {
        initializer()
    }

    // ── Relays ──────────────────────────────────────────────────────────────────

    /**
     * Bind one or more [Relay]s into this registry.
     *
     * Each relay starts observing any already-registered instance of its source class,
     * and again whenever a matching source store is registered in the future.
     *
     * ```kotlin
     * registry.bind(
     *     relay(from = AuthStore::class) {
     *         state<AuthState.SignedOut> { dispatch(CartStore::class, CartAction.Clear) }
     *     },
     *     relay(from = CartStore::class) {
     *         effect<CartEffect.CartChanged> { dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total)) }
     *     },
     * )
     * ```
     */
    public fun bind(vararg relays: Relay<*, *, *>) {
        relays.forEach { relay ->
            this.relays.add(element = relay)
            stores[relay.source]?.forEach { source ->
                val jobs = relay.apply(source = source, registry = this, scope = bridgeScope)
                relayJobs.getOrPut(source.id) { mutableListOf() }.addAll(jobs)
            }
        }
    }

    public operator fun Relay<*, *, *>.unaryPlus(): Unit = bind(this)

    // ── Global plugins ────────────────────────────────────────────────────────

    /**
     * Install a plugin factory globally on this registry.
     *
     * The [plugin] factory is called once per store — once immediately for every store currently
     * registered, and again for every store registered in the future — producing an independent
     * plugin instance for each store. The factory receives a [PluginScope] that exposes the
     * target [PluginScope.store] and a computed [PluginScope.name].
     *
     * ```kotlin
     * StoreRegistry(viewModelScope) {
     *     install { LoggingPlugin(tag = name) }
     *     install {
     *         plugin {
     *             onTransition { println("[$name] $fromState → $toState") }
     *         }
     *     }
     * }
     * ```
     *
     * Plugins begin receiving events from the next processed action onward — events that
     * occurred before this call are not replayed.
     *
     * Globally-registered plugins fire **after** any plugins installed directly on the store
     * at construction time, in installation order.
     *
     * Must be called from the same thread used for [register] and [unregister].
     */
    public fun install(plugin: PluginScope.() -> Plugin) {
        globalPlugins.add(plugin)
        stores.values.flatten().forEach { store -> store.install(PluginScope(store).plugin()) }
    }

    public operator fun Plugin.unaryPlus(): Unit = install { this@unaryPlus }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Add [store] to the registry, start any relay whose source is [store]'s class, and
     * return the store unchanged.
     *
     * Multiple instances of the same class may be registered. Registering the same
     * instance (same [Store.id]) twice throws [IllegalArgumentException].
     *
     * Only relays whose source matches [store] launch collectors here; relays that merely
     * target [store]'s class need no wiring, since they resolve targets lazily at emission time.
     *
     * The store is automatically stopped and unregistered when its owning [CoroutineScope] is
     * cancelled (e.g. `viewModelScope` cleared). This is implemented via [Store.invokeOnCompletion],
     * which fires on scope cancellation. Calling [Store.stop] directly does **not** trigger this
     * hook — call [unregister] manually if you stop the store before its scope is cancelled.
     */
    public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> register(
        store: Store<State, Action, Effect>,
    ): Store<State, Action, Effect> {
        val storeList = stores.getOrPut(key = store::class) { mutableListOf() }
        require(value = storeList.none { it.id == store.id }) {
            "A store with id \"${store.id}\" is already registered."
        }
        relays.forEach { relay ->
            if (store::class == relay.source) {
                val jobs = relay.apply(source = store, registry = this, scope = bridgeScope)
                relayJobs.getOrPut(store.id) { mutableListOf() }.addAll(jobs)
            }
        }
        globalPlugins.forEach { plugin -> store.install(PluginScope(store).plugin()) }
        storeList += store
        store.invokeOnCompletion {
            store.stop()
            unregister(store = store)
        }
        return store
    }

    /**
     * Remove [store] from the registry and cancel any relay collectors observing it.
     *
     * Identified by [Store.id], so the exact instance must be passed.
     * Does nothing if the store is not currently registered.
     */
    public fun unregister(store: Store<*, *, *>) {
        val list = stores[store::class] ?: return
        list.removeAll { it.id == store.id }
        if (list.isEmpty()) stores.remove(key = store::class)
        relayJobs.remove(store.id)?.forEach { it.cancel() }
    }

    /** Returns `true` if at least one instance of [kClass] is registered. */
    public operator fun contains(kClass: KClass<out Store<*, *, *>>): Boolean =
        stores[kClass]?.isNotEmpty() == true

    /** The set of classes that have at least one registered instance, in insertion order. */
    public val keys: Set<KClass<out Store<*, *, *>>> get() = stores.keys.toSet()

    /**
     * Retrieve a snapshot of all registered instances of [kClass].
     *
     * The returned list is an independent copy taken at call time: it never reflects later
     * registrations or removals, and mutating the registry while iterating it is safe.
     * Returns an empty list if none are registered.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> getAll(
        kClass: KClass<out Store<State, Action, Effect>>,
    ): List<Store<State, Action, Effect>> =
        (stores[kClass] as? List<Store<State, Action, Effect>>)?.toList() ?: emptyList()

    /**
     * Retrieve the first registered instance of [kClass], or `null` if none are registered.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> get(
        kClass: KClass<out Store<State, Action, Effect>>,
    ): Store<State, Action, Effect>? =
        stores[kClass]?.firstOrNull() as? Store<State, Action, Effect>

    /**
     * Retrieve the registered instance with the given [id], or `null` if not found.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> getById(
        id: String,
    ): Store<State, Action, Effect>? =
        stores.values.flatten().firstOrNull { it.id == id } as? Store<State, Action, Effect>
}

/**
 * Register this store in [registry] keyed by its class and return `this`.
 *
 * Fluent alternative to calling [StoreRegistry.register] directly, useful when
 * you hold a store reference and want to chain registration:
 *
 * ```kotlin
 * val cart = CartStore(cartMachine, scope).register(registry)
 * ```
 */
public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.register(
    registry: StoreRegistry,
): Store<State, Action, Effect> = also(registry::register)
