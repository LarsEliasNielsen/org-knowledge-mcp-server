package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.server.BuildInfo
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class ServerInfoToolTest {
    private val buildInfo = BuildInfo("knowledge-mcp-server", "abc123")

    @Test
    fun `is declared read-only`() {
        ServerInfoTool.definition.annotations?.readOnlyHint shouldBe true
    }

    @Test
    fun `returns name and version as text and structured content`() {
        val result = ServerInfoTool.call(buildInfo)

        result.isError shouldBe null
        result.content shouldBe listOf(TextContent("knowledge-mcp-server abc123"))
        result.structuredContent shouldBe buildJsonObject {
            put("name", "knowledge-mcp-server")
            put("version", "abc123")
        }
    }
}
