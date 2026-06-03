package tech.fika.monaka.runtime

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import tech.fika.monaka.binder.Binder
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store

/**
 * A keyed collection of [Store] instances that automatically applies [tech.fika.monaka.binder.Binder]s
 * as stores are registered.
 *
 * Multiple instances of the same [Store] subclass can be registered simultaneously,
 * each identified by its unique [Store.id]. Stores are deregistered individually
 * via [unregister], typically when they are disposed.
 *
 * The registry serves two purposes:
 * 1. **Keying** — stores are stored under their [KClass] so they can be retrieved
 *    without holding individual references.
 * 2. **Bridging** — [tech.fika.monaka.binder.Binder]s installed via [install] are applied automatically
 *    whenever a matching store is registered, wiring source and target instances
 *    together using [bridgeScope] for all bridge coroutines.
 *
 * ### Typical usage
 * ```kotlin
 * val registry = StoreRegistry(viewModelScope)
 *
 * registry.install(
 *     binder(
 *         from = AuthStateMachine::class,
 *         to = CartStateMachine::class,
 *     ) {
 *         bindState { authState ->
 *             when (authState) {
 *                 is AuthState.SignedIn  -> CartAction.LoadForUser(authState.user.id)
 *                 is AuthState.SignedOut -> CartAction.Clear
 *                 else                  -> null
 *             }
 *         }
 *     }
 * )
 *
 * AuthStateMachine(scope, authRepo).register(registry)
 * CartStateMachine(scope, cartRepo).register(registry)
 * ```
 *
 * Binders and stores may be installed in any order. When both sides of a binder
 * are already registered at install time, the binding is applied immediately.
 *
 * ### Disposing stores
 * ```kotlin
 * val auth = AuthStateMachine(scope, repo).register(registry)
 * // … later, when the store is no longer needed:
 * registry.unregister(auth)
 * ```
 *
 * @param bridgeScope The scope used for all bridge coroutines. Pass the same
 *                    scope that owns the stores so all work is cancelled together.
 */
class StoreRegistry(private val bridgeScope: CoroutineScope) {

    private val stores = LinkedHashMap<KClass<*>, MutableList<Store<*, *, *>>>()
    private val binders = mutableListOf<Binder<*, *, *, *>>()
    // (sourceId, targetId) → jobs launched by binder.apply for that pair
    private val binderJobs = HashMap<Pair<String, String>, MutableList<Job>>()

    // ── Binders ───────────────────────────────────────────────────────────────

    /**
     * Install one or more [Binder]s into this registry.
     *
     * Each binder is applied immediately to any already-registered source/target
     * pairs, and again whenever a matching store is registered in the future.
     *
     * ```kotlin
     * registry.install(
     *     binder(from = AuthStateMachine::class, to = CartStateMachine::class) {
     *         bindState { state -> if (state is AuthState.SignedOut) CartAction.Clear else null }
     *     },
     *     binder(from = CartStateMachine::class, to = CheckoutStateMachine::class) {
     *         bindEffect { effect ->
     *             if (effect is CartEffect.CartChanged) CheckoutAction.SyncCart(effect.items) else null
     *         }
     *     },
     * )
     * ```
     */
    fun install(vararg binders: Binder<*, *, *, *>) {
        for (binder in binders) {
            this.binders.add(element = binder)
            val sources = stores[binder.source] ?: continue
            val targets = stores[binder.target] ?: continue
            sources.forEach { source ->
                targets.forEach { target ->
                    val jobs = binder.apply(source = source, target = target, scope = bridgeScope)
                    binderJobs.getOrPut(source.id to target.id) { mutableListOf() }.addAll(jobs)
                }
            }
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Add [store] to the registry, apply all matching binders, and return the store unchanged.
     *
     * Multiple instances of the same class may be registered. Registering the same
     * instance (same [Store.id]) twice throws [IllegalArgumentException].
     *
     * Binders are applied before the store is added to the internal map, so a binder
     * from class `A` to class `A` connects the new instance to existing instances only.
     */
    fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> register(
        store: Store<State, Action, Effect>,
    ): Store<State, Action, Effect> {
        val storeList = stores.getOrPut(key = store::class) { mutableListOf() }
        require(value = storeList.none { it.id == store.id }) {
            "A store with id \"${store.id}\" is already registered."
        }
        binders.forEach { binder ->
            if (store::class == binder.source) {
                stores[binder.target]?.forEach { target ->
                    val jobs = binder.apply(source = store, target = target, scope = bridgeScope)
                    binderJobs.getOrPut(store.id to target.id) { mutableListOf() }.addAll(jobs)
                }
            }
            if (store::class == binder.target) {
                stores[binder.source]?.forEach { source ->
                    val jobs = binder.apply(source = source, target = store, scope = bridgeScope)
                    binderJobs.getOrPut(source.id to store.id) { mutableListOf() }.addAll(jobs)
                }
            }
        }
        storeList += store
        return store
    }

    /**
     * Remove [store] from the registry.
     *
     * Identified by [Store.id], so the exact instance must be passed.
     * Does nothing if the store is not currently registered.
     */
    fun unregister(store: Store<*, *, *>) {
        val list = stores[store::class] ?: return
        list.removeAll { it.id == store.id }
        if (list.isEmpty()) stores.remove(key = store::class)
        binderJobs.keys.filter { (sourceId, targetId) ->
            sourceId == store.id || targetId == store.id
        }.forEach { key ->
            binderJobs.remove(key)?.forEach { it.cancel() }
        }
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
 * val cart = CartStateMachine(scope, cartRepository).register(registry)
 * ```
 */
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.register(
    registry: StoreRegistry,
): Store<State, Action, Effect> = also(registry::register)
