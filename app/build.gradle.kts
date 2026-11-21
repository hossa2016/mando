plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hossa.mando"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hossa.mando"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "2.5"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("mando.keystore")
            storePassword = "mando123"
            keyAlias = "mando"
            keyPassword = "mando123"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { abortOnError = false; checkReleaseBuilds = false }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/BCKEY.DSA", "META-INF/BCKEY.SF") }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("com.flyfishxu:kadb:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
}
