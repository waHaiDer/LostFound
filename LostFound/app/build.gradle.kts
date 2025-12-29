plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.lostfound"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.lostfound"
        minSdk = 28
        targetSdk = 35
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

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // EXIF support for image rotation
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // OSMDroid for map view (Sprint 5 - Location Sharing)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Google Play Services Location for GPS
    implementation("com.google.android.gms:play-services-location:21.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}