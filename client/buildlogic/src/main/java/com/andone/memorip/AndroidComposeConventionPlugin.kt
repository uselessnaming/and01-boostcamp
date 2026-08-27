package com.andone.memorip

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val commonExtension = extensions.findByType(ApplicationExtension::class.java)
                ?: extensions.findByType(LibraryExtension::class.java)
                ?: error("memorip.android.application 혹은 memorip.android.library가 먼저 적용되어 있어야 합니다")

            configureCompose(commonExtension)
        }
    }

    private fun Project.configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
        commonExtension.buildFeatures.compose = true

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        dependencies {
            add("implementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("implementation", libs.findLibrary("androidx-ui").get())
            add("implementation", libs.findLibrary("androidx-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())
        }
    }
}