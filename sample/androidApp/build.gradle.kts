plugins {
    alias(libs.plugins.monaka.android.application)
}

android {
    namespace = "dev.gmvalentino.monaka"

    defaultConfig {
        applicationId = "dev.gmvalentino.monaka"
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
