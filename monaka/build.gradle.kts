plugins {
    alias(libs.plugins.monaka.kmp.targets)
    alias(libs.plugins.monaka.publication)
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.library"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
