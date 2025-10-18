plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
     id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
}

android {
    namespace = "com.example.potholedetector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.potholedetector"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true  // Enable View Binding
         compose = true
    }

    composeOptions {
    kotlinCompilerExtensionVersion = "1.5.3" // compatible with AGP 8.13
}

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.0")

    // Use lifecycle-runtime-compose 2.6.2 (stable)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Google Play Services - Location
    implementation("com.google.android.gms:play-services-location:21.0.1")
}
