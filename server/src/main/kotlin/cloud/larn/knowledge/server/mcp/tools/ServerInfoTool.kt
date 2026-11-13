package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.server.BuildInfo
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Returns the server name and the content version (git SHA) it was built from. */
object ServerInfoTool {
    const val NAME = "server_info"

    val definition = Tool(
        name = NAME,
        title = "Server info",
        description = "Returns the knowledge server's name and the content version (git SHA) it serves.",
        inputSchema = ToolSchema(),
        outputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") { put("type", "string") }
                putJsonObject("version") { put("type", "string") }
            },
            required = listOf("name", "version"),
        ),
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    fun call(buildInfo: BuildInfo): CallToolResult = CallToolResult(
        content = listOf(TextContent("${buildInfo.name} ${buildInfo.version}")),
        structuredContent = buildJsonObject {
            put("name", buildInfo.name)
            put("version", buildInfo.version)
        },
    )
}
