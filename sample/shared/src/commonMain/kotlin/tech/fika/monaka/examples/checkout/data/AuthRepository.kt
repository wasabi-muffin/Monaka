package tech.fika.monaka.examples.checkout.data

import kotlinx.coroutines.delay
import tech.fika.monaka.examples.checkout.data.User

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
