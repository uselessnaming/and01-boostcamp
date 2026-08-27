plugins {
    id("memorip.jvm.library")
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

dependencies {
    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutine
    implementation(libs.kotlinx.coroutines.core)

    // Paging
    implementation(libs.androidx.paging.common)
}