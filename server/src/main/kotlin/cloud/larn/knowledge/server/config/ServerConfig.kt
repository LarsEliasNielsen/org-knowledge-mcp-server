package cloud.larn.knowledge.server.config

import io.ktor.server.config.ApplicationConfig

/** Settings under the `knowledge` key in `application.yaml`. */
data class ServerConfig(
    /** Hostnames accepted in the `Host` header of `/mcp` requests. */
    val allowedHosts: List<String>,
)

fun ApplicationConfig.toServerConfig(): ServerConfig {
    val hosts = property("knowledge.allowedHosts").getString()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(hosts.isNotEmpty()) { "knowledge.allowedHosts must list at least one host" }
    return ServerConfig(allowedHosts = hosts)
}
