package cloud.larn.knowledge.server

import cloud.larn.knowledge.server.McpTestSupport.mcpTest
import cloud.larn.knowledge.server.McpTestSupport.text
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** list_docs and get_doc end to end over the MCP endpoint, against the fixture knowledge base. */
class DocToolsIntegrationTest {
    @Test
    fun `lists all tools as read-only`() = mcpTest { client ->
        val tools = client.listTools().tools

        tools.map { it.name } shouldContainExactly listOf("server_info", "list_docs", "get_doc")
        tools.all { it.annotations?.readOnlyHint == true } shouldBe true
    }

    @Test
    fun `list_docs filters by kind`() = mcpTest { client ->
        val result = client.callTool("list_docs", mapOf("kind" to "adr"))

        result.isError shouldBe null
        result.structuredContent!!["documents"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content } shouldContainExactly
            listOf("adr-0001-events")
    }

    @Test
    fun `get_doc returns a full document`() = mcpTest { client ->
        val result = client.callTool("get_doc", mapOf("id" to "org-review"))

        result.isError shouldBe null
        result.text shouldContain "# Review\n\nIntro text."
        result.text shouldEndWith "## Merging\n\nSquash-merge."
    }

    @Test
    fun `get_doc returns a single section`() = mcpTest { client ->
        val result = client.callTool("get_doc", mapOf("id" to "org-review", "section" to "reviewing"))

        result.text shouldEndWith "## Reviewing\n\n### FX-REV-001 Review quickly\nReview within a day."
    }

    @Test
    fun `get_doc reports an unknown section as a tool error`() = mcpTest { client ->
        val result = client.callTool("get_doc", mapOf("id" to "org-review", "section" to "nope"))

        result.isError shouldBe true
        result.text shouldBe "Document 'org-review' has no section 'nope'. Available sections: reviewing, merging."
    }

    @Test
    fun `get_doc reports an unknown document as a tool error`() = mcpTest { client ->
        val result = client.callTool("get_doc", mapOf("id" to "nope"))

        result.isError shouldBe true
        result.structuredContent!!["error"]!!.jsonPrimitive.content shouldBe "unknown_doc"
    }

    @Test
    fun `drafts are hidden by default and served when configured`() {
        mcpTest { client ->
            client.callTool("get_doc", mapOf("id" to "pay-draft")).isError shouldBe true
        }
        mcpTest(includeDrafts = true) { client ->
            client.callTool("get_doc", mapOf("id" to "pay-draft")).isError shouldBe null
        }
    }
}
