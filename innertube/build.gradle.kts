plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.tamed.music.innertube"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }

    sourceSets {
        getByName("main") {
            // Вказуємо, де лежать джерела KMP
            java.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin")
            res.srcDirs("src/commonMain/resources", "src/androidMain/res")
        }
    }
}

dependencies {
    // Ktor (мережа)
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-client-encoding:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    
    // Залежності, які були в Inner
    implementation("org.brotli:dec:0.1.2")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.0")
    implementation("com.google.re2j:re2j:1.7")
    implementation("org.mozilla:rhino:1.7.14")
    
    // Корутини та серіалізація
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
