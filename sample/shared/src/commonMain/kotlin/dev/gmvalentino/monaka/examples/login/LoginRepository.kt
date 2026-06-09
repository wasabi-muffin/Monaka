package dev.gmvalentino.monaka.examples.login

/** In real code this lives in the data layer, injected via DI. */
interface LoginRepository {
    suspend fun login(username: String, password: String): String
}
