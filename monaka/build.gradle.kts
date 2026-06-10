plugins {
    alias(libs.plugins.monaka.kmp.targets)
    alias(libs.plugins.monaka.explicit.api)
    alias(libs.plugins.monaka.publication)
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.library"
        compileSdk = Config.COMPILE_SDK
        minSdk = Config.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.log.kermit)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
