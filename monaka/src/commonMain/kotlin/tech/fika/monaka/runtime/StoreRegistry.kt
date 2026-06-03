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
 * @param bridgeScope The scope used for all relay coroutines. Pass the same
 *                    scope that owns the stores so all work is cancelled together.
 */
class StoreRegistry(private val bridgeScope: CoroutineScope) {

    private val stores = LinkedHashMap<KClass<*>, MutableList<Store<*, *, *>>>()
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
    val keys: Set<KClass<*>> get() = stores.keys.toSet()

    /**
     * Retrieve all registered instances of [kClass].
     *
     * Returns an empty list if none are registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> getAll(
        kClass: KClass<out Store<State, Action, Effect>>,
    ): List<Store<State, Action, Effect>> =
        (stores[kClass] as? List<Store<State, Action, Effect>>) ?: emptyList()

    /**
     * Retrieve the first registered instance of [kClass], or `null` if none are registered.
     */
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> get(
        kClass: KClass<out Store<State, Action, Effect>>,
    ): Store<State, Action, Effect>? = getAll(kClass = kClass).firstOrNull()

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
