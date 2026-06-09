import org.gradle.api.JavaVersion

internal object Config {
    const val GROUP = "dev.gmvalentino"
    const val NAME = "Monaka"
    const val VERSION = "0.1.0"
    const val COMPILE_SDK = 37
    const val MIN_SDK = 24
    const val TARGET_SDK = 36
    val javaVersion = JavaVersion.VERSION_11
}
