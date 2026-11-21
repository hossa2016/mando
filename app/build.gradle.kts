plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hossa.mando"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hossa.mando"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
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
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging {
        resources { excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/BCKEY.DSA", "META-INF/BCKEY.SF") }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
