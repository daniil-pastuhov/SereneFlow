import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing credentials live in a git-ignored keystore.properties next to this file
// (storeFile is resolved relative to this module dir), never in source control. See "Release
// signing" in README.md.
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.reader(Charsets.UTF_8).use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

// One version for both apps: Version.xcconfig is also the iOS project's base configuration.
val appVersion: String = providers.fileContents(rootProject.layout.projectDirectory.file("Version.xcconfig"))
    .asText.get()
    .let { Regex("""(?m)^MARKETING_VERSION\s*=\s*(\S+)""").find(it)?.groupValues?.get(1) }
    ?: error("No MARKETING_VERSION in Version.xcconfig")

android {
    namespace = "com.sereneflow.timer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sereneflow.timer"
        minSdk = 24
        targetSdk = 37
        // CI passes -PversionCode (see .github/workflows/ci.yml) so every upload to Google Play
        // gets a new, higher code; a local build falls back to this one.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = appVersion
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
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
