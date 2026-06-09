package dev.gmvalentino.monaka.examples.checkout.data

import kotlinx.coroutines.delay
import dev.gmvalentino.monaka.examples.checkout.data.CartItem

interface CartRepository {
    suspend fun loadCart(userId: String): List<CartItem>
}

internal class FakeCartRepository : CartRepository {
    override suspend fun loadCart(userId: String): List<CartItem> {
        delay(600)
        return listOf(
            CartItem("book1", "Kotlin in Action", 39.99, 1),
            CartItem("book2", "Clean Architecture", 29.99, 2),
            CartItem("book3", "Data-Intensive Applications", 49.99, 1),
        )
    }
}