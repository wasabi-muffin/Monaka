package tech.fika.monaka.examples.checkout.cart

import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.examples.checkout.data.CartRepository
import tech.fika.monaka.plugin.LoggingPlugin

class CartStateMachine(
    cartRepository: CartRepository,
) : StateMachine<CartState, CartAction, CartEffect> by stateMachine(builder = {
    initialState(CartState.Empty)

    state<CartState.Empty> {
        on<CartAction.LoadForUser> {
            transition {
                CartState.Loading(action.userId)
            }
        }
    }

    state<CartState.Loading> {
        onEnter {
            val items = cartRepository.loadCart(state.userId)
            transition { CartState.WithItems(state.userId, items) }
        }
    }

    state<CartState.WithItems> {
        on<CartAction.AddItem> {
            val updated = state.items + action.item
            transition { CartState.WithItems(state.userId, updated) }
            sideEffect(CartEffect.CartChanged(state.userId, updated, updated.sumOf { it.subtotal }))
        }
        on<CartAction.RemoveItem> {
            val updated = state.items.filter { it.productId != action.productId }
            transition { CartState.WithItems(state.userId, updated) }
            sideEffect(CartEffect.CartChanged(state.userId, updated, updated.sumOf { it.subtotal }))
        }
        on<CartAction.UpdateQuantity> {
            val updated = state.items
                .map { if (it.productId == action.productId) it.copy(quantity = action.quantity) else it }
                .filter { it.quantity > 0 }
            transition { CartState.WithItems(state.userId, updated) }
            sideEffect(CartEffect.CartChanged(state.userId, updated, updated.sumOf { it.subtotal }))
        }
    }

    state<CartState> {
        on<CartAction.Clear> { transition { CartState.Empty } }
    }

    install(LoggingPlugin(tag = "Cart"))
})
