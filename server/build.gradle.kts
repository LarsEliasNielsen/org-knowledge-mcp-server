plugins {
    id("knowledge.ktor-server")
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ktor {
    fatJar {
        archiveFileName = "knowledge-mcp-server.jar"
    }
}

dependencies {
    implementation(project(":infrastructure"))

    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.mcp.server)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mcp.client)
}

// Content version = git SHA of the build, bundled as a classpath resource.
// Falls back to "dev" outside a git checkout or before the first commit.
val gitSha: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "dev" } }.orElse("dev")

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val version = gitSha
    val outputDir = layout.buildDirectory.dir("generated/build-info")
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("build-info.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=${version.get()}\n")
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo)
}
