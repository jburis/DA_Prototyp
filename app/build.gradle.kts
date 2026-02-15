plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.da_prototyp_ocr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.da_prototyp_ocr"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val camerax_version = "1.3.4"

dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Retrofit für Netzwerkanfragen
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

// Gson-Converter, um JSON automatisch in Java/Kotlin-Objekte umzuwandeln
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")


    // CameraX
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // QR-Code-Scanner (ML Kit Barcode Scanning)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    implementation(libs.appcompat)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
