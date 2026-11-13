package cloud.larn.knowledge.server

import cloud.larn.knowledge.application.KnowledgeRepository
import cloud.larn.knowledge.infrastructure.source.InMemoryKnowledgeRepository
import cloud.larn.knowledge.server.config.ServerConfig
import cloud.larn.knowledge.server.config.toServerConfig
import cloud.larn.knowledge.server.http.healthRoute
import cloud.larn.knowledge.server.mcp.createMcpServer
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp

/** Entry point referenced from `application.yaml`. Fails startup if the knowledge base is invalid. */
fun Application.module() {
    val config = environment.config.toServerConfig()
    val knowledge = InMemoryKnowledgeRepository(loadKnowledgeBase(config, log))
    configureServer(config, BuildInfo.load(), knowledge)
}

/** Composition root. Tests call this directly with an explicit config and knowledge base. */
fun Application.configureServer(config: ServerConfig, buildInfo: BuildInfo, knowledge: KnowledgeRepository) {
    routing {
        healthRoute(buildInfo)
    }
    mcpStatelessStreamableHttp(path = "/mcp", allowedHosts = config.allowedHosts) {
        createMcpServer(buildInfo, knowledge)
    }
}
