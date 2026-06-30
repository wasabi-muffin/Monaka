plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.monaka.publication)
}

mavenPublishing {
    pom {
        description.set("Gradle plugin for Monaka that provides state machine YAML export, stub code genration and PlantUML diagram generation tasks.")
    }
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.snakeyaml)
    testImplementation(gradleApi())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("monakaGradlePlugin") {
            id = "dev.gmvalentino.monaka.gradle-plugin"
            implementationClass = "dev.gmvalentino.monaka.gradle.MonakaPlugin"
        }
    }
}
