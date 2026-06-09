plugins {
    kotlin("jvm")
}

group = "dev.gmvalentino.monaka"
version = "unspecified"

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
