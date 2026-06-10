plugins {
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.compose)
    alias(libs.plugins.monaka.explicit.api)
    alias(libs.plugins.monaka.publication)
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.compose"
        compileSdk = Config.COMPILE_SDK
        minSdk = Config.MIN_SDK
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )

    sourceSets {
        commonMain.dependencies {
            api(project(":monaka"))
            implementation(libs.compose.runtime)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
