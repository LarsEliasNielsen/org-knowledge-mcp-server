package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.server.BuildInfo
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Returns the server name, the content version (git SHA) it was built from, and the size of its knowledge base. */
object ServerInfoTool {
    const val NAME = "server_info"

    val definition = Tool(
        name = NAME,
        title = "Server info",
        description = "Returns the knowledge server's name, the content version (git SHA) it serves, " +
            "and how many documents and rules it holds.",
        inputSchema = ToolSchema(),
        outputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") { put("type", "string") }
                putJsonObject("version") { put("type", "string") }
                putJsonObject("documents") { put("type", "integer") }
                putJsonObject("rules") { put("type", "integer") }
            },
            required = listOf("name", "version", "documents", "rules"),
        ),
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    fun call(buildInfo: BuildInfo, knowledgeBase: KnowledgeBase): CallToolResult {
        val documents = knowledgeBase.docs.size
        val rules = knowledgeBase.docs.sumOf { it.rules.size }
        return CallToolResult(
            content = listOf(TextContent("${buildInfo.name} ${buildInfo.version}: $documents documents, $rules rules")),
            structuredContent = buildJsonObject {
                put("name", buildInfo.name)
                put("version", buildInfo.version)
                put("documents", documents)
                put("rules", rules)
            },
        )
    }
}
