plugins {
    `kotlin-dsl`
}

dependencies {
    // Plugins applied by modules live here, so Kotlin's Gradle plugin loads once in one classloader.
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ktor.gradle.plugin)
}
