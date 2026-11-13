package cloud.larn.knowledge.server.config

import io.ktor.server.config.ApplicationConfig
import java.nio.file.Path

/** Settings under the `knowledge` key in `application.yaml`. */
data class ServerConfig(
    /** Hostnames accepted in the `Host` header of `/mcp` requests. */
    val allowedHosts: List<String>,
    /** Load knowledge from this directory instead of the bundled classpath copy (local editing). */
    val knowledgeDir: Path? = null,
    /** Serve draft documents too. Off in production; for authors previewing locally. */
    val includeDrafts: Boolean = false,
)

fun ApplicationConfig.toServerConfig(): ServerConfig {
    val hosts = property("knowledge.allowedHosts").getString()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(hosts.isNotEmpty()) { "knowledge.allowedHosts must list at least one host" }

    val includeDrafts = propertyOrNull("knowledge.includeDrafts")?.getString()?.trim()?.lowercase() ?: "false"
    require(includeDrafts in setOf("true", "false")) { "knowledge.includeDrafts must be true or false, was '$includeDrafts'" }

    return ServerConfig(
        allowedHosts = hosts,
        knowledgeDir = propertyOrNull("knowledge.dir")?.getString()?.trim()?.takeIf { it.isNotEmpty() }?.let(Path::of),
        includeDrafts = includeDrafts.toBooleanStrict(),
    )
}
