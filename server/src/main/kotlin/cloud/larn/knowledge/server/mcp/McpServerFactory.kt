package cloud.larn.knowledge.server.mcp

import cloud.larn.knowledge.application.KnowledgeRepository
import cloud.larn.knowledge.server.BuildInfo
import cloud.larn.knowledge.server.mcp.tools.ServerInfoTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/** Builds the MCP server. Runs per request under the stateless transport, so it must stay cheap. */
fun createMcpServer(buildInfo: BuildInfo, knowledge: KnowledgeRepository): Server {
    val server = Server(
        serverInfo = Implementation(name = buildInfo.name, version = buildInfo.version),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
        ),
    )
    server.addTool(ServerInfoTool.definition) { ServerInfoTool.call(buildInfo, knowledge.current()) }
    return server
}
