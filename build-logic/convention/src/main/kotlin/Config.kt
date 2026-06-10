import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

object Config {
    const val GROUP = "dev.gmvalentino.monaka"
    const val NAME = "Monaka"
    const val VERSION = "0.1.0"
    const val COMPILE_SDK = 37
    const val MIN_SDK = 24
    const val TARGET_SDK = 36
    val javaVersion = JavaVersion.VERSION_11
    val jvmTarget = JvmTarget.JVM_11
    val kotlinApiVersion = KotlinVersion.KOTLIN_2_0
    val kotlinLanguageVersion = KotlinVersion.KOTLIN_2_4
}
