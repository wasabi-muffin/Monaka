package dev.gmvalentino.monaka.examples.checkout.data

data class CartItem(
    val productId: String,
    val name: String,
    val priceEach: Double,
    val quantity: Int,
) {
    val subtotal: Double get() = priceEach * quantity
}
