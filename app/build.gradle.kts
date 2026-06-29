import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val secretsFile = File(rootDir, "secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
val proxyUrl = secrets.getProperty("MASV_PROXY_URL")?.trim('"') ?: ""

android {
    namespace = "com.cambrian.masv_dev"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cambrian.masv_dev"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MASV_PROXY_URL", "\"$proxyUrl\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}