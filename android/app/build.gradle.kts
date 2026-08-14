import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY", "") ?: ""

android {
    namespace = "mx.reddeayuda.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "mx.reddeayuda.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.2"
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey.replace("\"", "\\\"")}\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.ifBlank { " " }
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":transport-ble"))
    implementation(project(":transport-wifi"))
    implementation(project(":platform"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
