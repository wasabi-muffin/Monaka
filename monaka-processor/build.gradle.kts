plugins {
    kotlin("jvm")
}

group = "tech.fika.monaka"
version = "unspecified"

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
