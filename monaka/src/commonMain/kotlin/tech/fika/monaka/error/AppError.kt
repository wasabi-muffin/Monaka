package tech.fika.monaka.error

open class AppError : Throwable() {
    class Unknown(message: String? = null, cause: Throwable? = null) : AppError()
}
