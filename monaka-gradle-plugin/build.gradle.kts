plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.21"
}

group = "dev.gmvalentino.monaka"
version = "0.1.0"

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
    testImplementation(gradleApi())
}

gradlePlugin {
    plugins {
        register("monakaYamlExport") {
            id = "dev.gmvalentino.monaka.yaml-export"
            implementationClass = "dev.gmvalentino.monaka.gradle.MonakaPlugin"
        }
    }
}
