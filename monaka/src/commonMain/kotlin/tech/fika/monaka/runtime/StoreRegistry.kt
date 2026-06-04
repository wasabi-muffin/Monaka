package tech.fika.monaka.runtime

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.relay.Relay

/**
 * A keyed collection of [Store] instances that automatically applies [tech.fika.monaka.relay.Relay]s
 * as stores are registered.
 *
 * Multiple instances of the same [Store] subclass can be registered simultaneously,
 * each identified by its unique [Store.id]. Stores are deregistered individually
 * via [unregister], typically when they are disposed.
 *
 * The registry serves two purposes:
 * 1. **Keying** — stores are stored under their [KClass] so they can be retrieved
 *    without holding individual references.
 * 2. **Relaying** — [tech.fika.monaka.relay.Relay]s installed via [install] start observing a
 *    source store as soon as a matching instance is registered. Relay targets are resolved
 *    lazily through the registry at emission time, so a relay reaches whatever target stores
 *    are registered when an event fires. All relay coroutines run in [bridgeScope].
 *
 * ### Typical usage
 * ```kotlin
 * val registry = StoreRegistry(viewModelScope)
 *
 * registry.install(
 *     relay(from = AuthStore::class) {
 *         state<AuthState.SignedIn>  { dispatch<CartStore>(CartAction.LoadForUser(event.user.id)) }
 *         state<AuthState.SignedOut> { dispatch<CartStore>(CartAction.Clear) }
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
 * ### Disposing stores
 * ```kotlin
 * val auth = AuthStore(authMachine, scope).register(registry)
 * // … later, when the store is no longer needed:
 * registry.unregister(auth)
 * ```
 *
 * ### Threading
 * [StoreRegistry] is **not thread-safe**. All calls to [register], [unregister], [install],
 * and [get]/[getAll] must be made from the same thread — typically the main thread on Android
 * and iOS. Pass a [bridgeScope] confined to that same thread (e.g. `viewModelScope`, which
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
class StoreRegistry(private val bridgeScope: CoroutineScope) {

    private val stores = LinkedHashMap<KClass<out Store<*, *, *>>, MutableList<Store<*, *, *>>>()
    private val relays = mutableListOf<Relay<*, *, *>>()
    // sourceId → collector jobs launched by relay.apply for that source store
    private val relayJobs = HashMap<String, MutableList<Job>>()

    // ── Relays ──────────────────────────────────────────────────────────────────

    /**
     * Install one or more [Relay]s into this registry.
     *
     * Each relay starts observing any already-registered instance of its source class,
     * and again whenever a matching source store is registered in the future.
     *
     * ```kotlin
     * registry.install(
     *     relay(from = AuthStore::class) {
     *         state<AuthState.SignedOut> { dispatch<CartStore>(CartAction.Clear) }
     *     },
     *     relay(from = CartStore::class) {
     *         effect<CartEffect.CartChanged> { dispatch<CheckoutStore>(CheckoutAction.SyncCart(event.items, event.total)) }
     *     },
     * )
     * ```
     */
    fun install(vararg relays: Relay<*, *, *>) {
        for (relay in relays) {
            this.relays.add(element = relay)
            stores[relay.source]?.forEach { source ->
                val jobs = relay.apply(source = source, registry = this, scope = bridgeScope)
                relayJobs.getOrPut(source.id) { mutableListOf() }.addAll(jobs)
            }
        }
    }

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
     */
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> register(
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
        storeList += store
        return store
    }

    /**
     * Remove [store] from the registry and cancel any relay collectors observing it.
     *
     * Identified by [Store.id], so the exact instance must be passed.
     * Does nothing if the store is not currently registered.
     */
    fun unregister(store: Store<*, *, *>) {
        val list = stores[store::class] ?: return
        list.removeAll { it.id == store.id }
        if (list.isEmpty()) stores.remove(key = store::class)
        relayJobs.remove(store.id)?.forEach { it.cancel() }
    }

    /** Returns `true` if at least one instance of [kClass] is registered. */
    operator fun contains(kClass: KClass<out Store<*, *, *>>): Boolean =
        stores[kClass]?.isNotEmpty() == true

    /** The set of classes that have at least one registered instance, in insertion order. */
    val keys: Set<KClass<out Store<*, *, *>>> get() = stores.keys.toSet()

    /**
     * Retrieve a snapshot of all registered instances of [kClass].
     *
     * The returned list is an independent copy taken at call time: it never reflects later
     * registrations or removals, and mutating the registry while iterating it is safe.
     * Returns an empty list if none are registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> getAll(
        kClass: KClass<out Store<State, Action, Effect>>,
    ): List<Store<State, Action, Effect>> =
        (stores[kClass] as? List<Store<State, Action, Effect>>)?.toList() ?: emptyList()

    /**
     * Retrieve the first registered instance of [kClass], or `null` if none are registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> get(
        kClass: KClass<out Store<State, Action, Effect>>,
    ): Store<State, Action, Effect>? =
        stores[kClass]?.firstOrNull() as? Store<State, Action, Effect>

    /**
     * Retrieve the registered instance with the given [id], or `null` if not found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> getById(
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
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.register(
    registry: StoreRegistry,
): Store<State, Action, Effect> = also(registry::register)

/**
 * Register this store in [registry] and automatically cancel it and [StoreRegistry.unregister]
 * it when the store's own scope completes.
 *
 * The unregister hook is idempotent, so a later manual [StoreRegistry.unregister] is a harmless
 * no-op. If the store's scope is already completed when this is called, the store is registered
 * and then immediately unregistered.
 *
 * ```kotlin
 * val cart = CartStore(cartMachine, scope = viewModelScope)
 *     .registerScoped(registry)
 * ```
 */
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.registerScoped(
    registry: StoreRegistry,
): Store<State, Action, Effect> {
    val store = this
    registry.register(store = store)
    invokeOnCompletion {
        store.cancel()
        registry.unregister(store = store)
    }
    return store
}
