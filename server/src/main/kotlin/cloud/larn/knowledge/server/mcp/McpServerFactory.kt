package cloud.larn.knowledge.server.mcp

import cloud.larn.knowledge.application.GetDoc
import cloud.larn.knowledge.application.GetGuidelines
import cloud.larn.knowledge.application.KnowledgeRepository
import cloud.larn.knowledge.application.ListDocs
import cloud.larn.knowledge.application.Visibility
import cloud.larn.knowledge.server.BuildInfo
import cloud.larn.knowledge.server.mcp.tools.GetDocTool
import cloud.larn.knowledge.server.mcp.tools.GetGuidelinesTool
import cloud.larn.knowledge.server.mcp.tools.ListDocsTool
import cloud.larn.knowledge.server.mcp.tools.ServerInfoTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/** The tools, built once at startup and shared by the per-request servers. */
class KnowledgeTools(knowledge: KnowledgeRepository, visibility: Visibility) {
    val listDocs = ListDocsTool(ListDocs(knowledge, visibility))
    val getDoc = GetDocTool(GetDoc(knowledge, visibility))
    val getGuidelines = GetGuidelinesTool(GetGuidelines(knowledge, visibility))
}

/** Builds the MCP server. Runs per request under the stateless transport, so it must stay cheap. */
fun createMcpServer(buildInfo: BuildInfo, knowledge: KnowledgeRepository, tools: KnowledgeTools): Server {
    val server = Server(
        serverInfo = Implementation(name = buildInfo.name, version = buildInfo.version),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
        ),
        instructions = SERVER_INSTRUCTIONS,
    )
    server.addTool(ServerInfoTool.definition) { ServerInfoTool.call(buildInfo, knowledge.current()) }
    server.addTool(tools.listDocs.definition) { tools.listDocs.call(it.arguments) }
    server.addTool(tools.getDoc.definition) { tools.getDoc.call(it.arguments) }
    server.addTool(tools.getGuidelines.definition) { tools.getGuidelines.call(it.arguments) }
    return server
}
