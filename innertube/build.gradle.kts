plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.tamed.music.innertube"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-client-encoding:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:2.3.12")
            implementation("org.brotli:dec:0.1.2")
            implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.0")
            implementation("com.google.re2j:re2j:1.7")
            implementation("org.mozilla:rhino:1.7.14")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
