package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.Rule
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.Scope
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
    fun `returns name, version and knowledge base size as text and structured content`() {
        val doc = KnowledgeDoc(
            id = DocId("org-a"),
            title = "A",
            kind = Kind.GUIDELINE,
            layer = Layer.ORG,
            scope = Scope.EVERYONE,
            owner = "owner",
            status = DocStatus.ACTIVE,
            summary = "Summary.",
            rules = listOf(Rule(RuleId("ORG-A-001"), "One", ""), Rule(RuleId("ORG-A-002"), "Two", "")),
            sourcePath = "org/a.md",
        )

        val result = ServerInfoTool.call(buildInfo, KnowledgeBase(listOf(doc)))

        result.isError shouldBe null
        result.content shouldBe listOf(TextContent("knowledge-mcp-server abc123: 1 documents, 2 rules"))
        result.structuredContent shouldBe buildJsonObject {
            put("name", "knowledge-mcp-server")
            put("version", "abc123")
            put("documents", 1)
            put("rules", 2)
        }
    }
}
