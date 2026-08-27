plugins {
    `kotlin-dsl`
}

group = "com.andone.memorip.build_logic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "memorip.android.application"
            implementationClass = "com.andone.memorip.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "memorip.android.library"
            implementationClass = "com.andone.memorip.AndroidLibraryConventionPlugin"
        }
        register("androidHilt") {
            id = "memorip.android.hilt"
            implementationClass = "com.andone.memorip.AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "memorip.jvm.library"
            implementationClass = "com.andone.memorip.JvmLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "memorip.android.compose"
            implementationClass = "com.andone.memorip.AndroidComposeConventionPlugin"
        }
    }
}