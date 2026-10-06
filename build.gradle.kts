// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android Application Gradle plugin
    alias(libs.plugins.android.application) apply false

    // Kotlin Android plugin
    alias(libs.plugins.kotlin.android) apply false

    // Kotlin Compose compiler plugin (built into Kotlin 2.0+)
    alias(libs.plugins.kotlin.compose) apply false

    // Kotlin Symbol Processing (KSP) for lightweight Room annotation processing
    alias(libs.plugins.ksp) apply false
}
