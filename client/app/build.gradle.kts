plugins {
    id("memorip.android.application")
    id("memorip.android.compose")
    id("memorip.android.hilt")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.andone.memorip"

    defaultConfig {
        applicationId = "com.andone.memorip"
        versionCode = 9
        versionName = "1.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    androidResources {
        noCompress += listOf("tflite", "txt")
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Firebase Analytics & Crashlytics
    implementation(platform(libs.firebase))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Hilt
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Java 8+ API desugaring (java.time 라이브러리 지원)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Coil3
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
}