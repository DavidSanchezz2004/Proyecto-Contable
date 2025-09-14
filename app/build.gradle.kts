plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("org.jetbrains.kotlin.kapt") // ← usar kapt


}

android {
    namespace = "pe.ds.transferapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "pe.ds.transferapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            // Necesario por Apache POI (evita conflictos de empaquetado)
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    // Core UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ML Kit OCR + await()
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.android.gms:play-services-tasks:18.0.2")

    // SweetAlert
    implementation("com.github.f0ris.sweetalert:library:1.6.2")

    // Seguridad: PIN cifrado
    implementation("androidx.security:security-crypto:1.1.0")

    // BiometricPrompt para DEVICE_CREDENTIAL (patrón/PIN del sistema)
    implementation("androidx.biometric:biometric:1.1.0")

    // Desugaring (java.time, etc.)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}



configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
    exclude(group = "org.apache.logging.log4j", module = "log4j-core")
}

kapt {
    correctErrorTypes = true
}