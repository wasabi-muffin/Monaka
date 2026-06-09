plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.monaka.publication)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
