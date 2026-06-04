plugins {
    alias(libs.plugins.monaka.android.application)
    alias(libs.plugins.compiler.plugin)
}

android {
    namespace = "tech.fika.monaka"

    defaultConfig {
        applicationId = "tech.fika.monaka"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":sample:shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
