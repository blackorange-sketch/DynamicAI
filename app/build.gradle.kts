plugins {
    id("com.android.application")
        id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dynamicai.launcher"
    compileSdk = 37

    defaultConfig {
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }

        applicationId = "com.dynamicai.launcher"
        minSdk = 33
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        resourceConfigurations += listOf("uk", "en")
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (!path.isNullOrEmpty()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("KEYSTORE_PATH").isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Inner для YouTube Music API
    // Media3 для відтворення
    // OkHttp (для Inner)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Ktor (Inner використовує Ktor)

    // NewPipe Extractor для YouTube Music без реклами
    // Media3 для відтворення аудіо
    // OkHttp для Downloader
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Glance для віджета
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    // Core library desugaring


    implementation("androidx.fragment:fragment-ktx:1.8.5")
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
