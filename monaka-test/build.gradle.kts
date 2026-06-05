plugins {
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.publication)
}

kotlin {
    android {
        namespace = "tech.fika.monaka.test.library"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":monaka"))
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            api(libs.kotest.assertions)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
