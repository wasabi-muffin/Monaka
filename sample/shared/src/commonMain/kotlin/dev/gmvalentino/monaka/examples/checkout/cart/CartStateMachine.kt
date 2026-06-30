package dev.gmvalentino.monaka.examples.checkout.cart

import dev.gmvalentino.monaka.dsl.StateMachine
import dev.gmvalentino.monaka.dsl.stateMachine
import dev.gmvalentino.monaka.examples.checkout.data.CartRepository

class CartStateMachine(
    cartRepository: CartRepository,
) : StateMachine<CartState, CartAction, CartEffect> by stateMachine(builder = {
    initialState(CartState.Empty)

    state<CartState.Empty> {
        on<CartAction.LoadForUser> {
            transition(state.toLoading(userId = action.userId))
        }
    }

    state<CartState.Loading> {
        onEnter {
            val items = cartRepository.loadCart(state.userId)
            transition(state.toWithItems(items = items))
        }
    }

    state<CartState.WithItems> {
        on<CartAction.AddItem> {
            val updated = state.items + action.item
            transition(state.copy(items = updated))
            sideEffect(CartEffect.CartChanged(state.userId, updated, updated.sumOf { it.subtotal }))
        }
        on<CartAction.RemoveItem> {
            val updated = state.items.filter { it.productId != action.productId }
            transition(state.copy(items = updated))
            sideEffect(CartEffect.CartChanged(state.userId, updated, updated.sumOf { it.subtotal }))
        }
        on<CartAction.UpdateQuantity> {
            val updated = state.items
                .map { if (it.productId == action.productId) it.copy(quantity = action.quantity) else it }
                .filter { it.quantity > 0 }
            transition(state.copy(items = updated))
            sideEffect(CartEffect.CartChanged(state.userId, updated, updated.sumOf { it.subtotal }))
        }
    }

    state<CartState> {
        on<CartAction.Clear> { transition(CartState.Empty) }
    }
})
