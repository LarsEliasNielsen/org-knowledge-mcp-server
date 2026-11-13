plugins {
    id("knowledge.kotlin-serialization")
}

dependencies {
    api(project(":application"))

    implementation(libs.commonmark)
    implementation(libs.kaml)
    // kaml is built against an older serialization runtime; align it with the rest of the project.
    implementation(libs.kotlinx.serialization.core)
}
