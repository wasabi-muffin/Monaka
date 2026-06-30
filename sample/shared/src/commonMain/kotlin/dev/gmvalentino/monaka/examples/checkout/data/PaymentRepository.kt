package dev.gmvalentino.monaka.examples.checkout.data

import dev.gmvalentino.monaka.examples.checkout.data.CartItem
import kotlinx.coroutines.delay
import kotlin.random.Random

interface PaymentRepository {
    suspend fun charge(userId: String, items: List<CartItem>, total: Double): String // returns orderId
}

internal class FakePaymentRepository : PaymentRepository {
    override suspend fun charge(userId: String, items: List<CartItem>, total: Double): String {
        delay(1500)
        return "ORD-${Random.nextInt(100_000)}"
    }
}
