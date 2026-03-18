plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.multinet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.multinet"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5"
    }

    signingConfigs {
        create("release") {
            // Keystore details read from ~/.gradle/gradle.properties (never committed)
            val storeFilePath: String? by project
            val storePass: String? by project
            val keyAliasName: String? by project
            val keyPass: String? by project
            storeFile     = storeFilePath?.let { file(it) }
            storePassword = storePass
            keyAlias      = keyAliasName
            keyPassword   = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig   = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }


}

dependencies {
    // Compose BOM — keeps all Compose library versions in sync
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Room — SQLite ORM for storing download state
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")       // coroutine extensions for Room
    ksp("androidx.room:room-compiler:2.6.1")              // generates Room code at build time

    // OkHttp — HTTP client for downloading files
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines — structured concurrency for background work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
