package cloud.larn.knowledge.server

import cloud.larn.knowledge.server.config.ServerConfig
import cloud.larn.knowledge.server.config.toServerConfig
import cloud.larn.knowledge.server.http.healthRoute
import cloud.larn.knowledge.server.mcp.createMcpServer
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp

/** Entry point referenced from `application.yaml`. */
fun Application.module() {
    configureServer(environment.config.toServerConfig(), BuildInfo.load())
}

/** Composition root. Tests call this directly with an explicit config. */
fun Application.configureServer(config: ServerConfig, buildInfo: BuildInfo) {
    routing {
        healthRoute(buildInfo)
    }
    mcpStatelessStreamableHttp(path = "/mcp", allowedHosts = config.allowedHosts) {
        createMcpServer(buildInfo)
    }
}
