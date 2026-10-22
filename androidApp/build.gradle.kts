plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.sereneflow.timer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sereneflow.timer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        getByName("debug") {
            keyAlias = "debug"
            keyPassword = "android"
            storePassword = "android"
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
