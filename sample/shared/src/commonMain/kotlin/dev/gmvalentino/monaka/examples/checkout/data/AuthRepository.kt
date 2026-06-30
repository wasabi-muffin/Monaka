package dev.gmvalentino.monaka.examples.checkout.data

import dev.gmvalentino.monaka.examples.checkout.data.User
import kotlinx.coroutines.delay

interface AuthRepository {
    suspend fun signIn(username: String, password: String): User
}

internal class FakeAuthRepository : AuthRepository {
    override suspend fun signIn(username: String, password: String): User {
        delay(1000)
        if (password != "password") throw Exception("Wrong password. Hint: \"password\"")
        return User(id = "u_${username.lowercase()}", displayName = username)
    }
}
