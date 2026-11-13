// Shared setup for every module: Kotlin JVM on the JDK 21 toolchain, JUnit Platform tests.

plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "cloud.larn.knowledge"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
    testImplementation(kotlin("test"))
    testImplementation(libs.findLibrary("kotest-assertions-core").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
