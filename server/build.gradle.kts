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

// SampleProjectTest checks examples/sample-project against the bundled knowledge; rerun tests when it changes.
tasks.test {
    val sampleProject = rootProject.layout.projectDirectory.dir("examples/sample-project")
    inputs.dir(sampleProject).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("sampleProject", sampleProject.asFile.absolutePath)
}

// Content version = git SHA of the build, bundled as a classpath resource. A build with uncommitted or untracked
// changes gets a "-dirty" suffix, since its content doesn't match the commit. Falls back to "dev" outside a git
// checkout or before the first commit.
val gitHead: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }
val gitDirty: Provider<Boolean> = providers.exec {
    commandLine("git", "status", "--porcelain")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.isNotBlank() }
val gitSha: Provider<String> = gitHead.zip(gitDirty) { head, dirty ->
    when {
        head.isEmpty() -> "dev"
        dirty -> "$head-dirty"
        else -> head
    }
}.orElse("dev")

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

// Bundles the repo's knowledge/ folder as classpath resources under knowledge/, plus knowledge/index.txt
// listing every file, since classpath directories can't be listed inside a JAR.
val bundleKnowledge = tasks.register("bundleKnowledge") {
    val sourceDir = rootProject.layout.projectDirectory.dir("knowledge")
    val outputDir = layout.buildDirectory.dir("generated/knowledge")
    inputs.dir(sourceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir)
    doLast {
        val source = sourceDir.asFile
        val target = outputDir.get().dir("knowledge").asFile
        target.deleteRecursively()
        val files = source.walkTopDown()
            .onEnter { it == source || !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { it.relativeTo(source).invariantSeparatorsPath }
            .sorted()
            .toList()
        files.forEach { source.resolve(it).copyTo(target.resolve(it)) }
        target.resolve("index.txt").writeText(files.joinToString("\n", postfix = "\n"))
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo)
    resources.srcDir(bundleKnowledge)
}
