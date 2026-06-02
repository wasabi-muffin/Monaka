package tech.fika.monaka.examples.checkout.data

import kotlin.random.Random
import kotlinx.coroutines.delay
import tech.fika.monaka.examples.checkout.data.CartItem

interface PaymentRepository {
    suspend fun charge(userId: String, items: List<CartItem>, total: Double): String // returns orderId
}

internal class FakePaymentRepository : PaymentRepository {
    override suspend fun charge(userId: String, items: List<CartItem>, total: Double): String {
        delay(1500)
        return "ORD-${Random.nextInt(100_000)}"
    }
}