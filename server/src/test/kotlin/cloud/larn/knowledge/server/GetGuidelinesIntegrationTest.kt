package cloud.larn.knowledge.server

import cloud.larn.knowledge.server.McpTestSupport.mcpTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** get_guidelines end to end over the MCP endpoint, against the fixture knowledge base. */
class GetGuidelinesIntegrationTest {
    private fun CallToolResult.ruleIds() =
        structuredContent!!["rules"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private fun CallToolResult.rule(id: String) =
        structuredContent!!["rules"]!!.jsonArray.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == id }

    private fun CallToolResult.warnings() = structuredContent!!["warnings"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun `org-wide rules only for an empty context`() = mcpTest { client ->
        val result = client.callTool("get_guidelines", emptyMap())

        result.isError shouldBe null
        result.ruleIds() shouldContainExactly listOf("FX-ARCH-001", "FX-REV-001")
        result.warnings().shouldBeEmpty()
    }

    @Test
    fun `one language adds its active rules`() = mcpTest { client ->
        client.callTool("get_guidelines", mapOf("languages" to listOf("java"))).ruleIds() shouldContainExactly
            listOf("FX-ARCH-001", "FX-REV-001", "FX-JAVA-002", "FX-JAVA-003")
    }

    @Test
    fun `two languages and a team follow the requested order and apply team overrides`() = mcpTest { client ->
        val result = client.callTool("get_guidelines", mapOf("team" to "payments", "languages" to listOf("kotlin", "java")))

        result.ruleIds() shouldContainExactly listOf("FX-ARCH-001", "FX-REV-001", "FX-KT-001", "FX-JAVA-003", "FX-PAY-001")
        result.rule("FX-PAY-001")["replaces"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainExactly listOf("FX-JAVA-002")
    }

    @Test
    fun `applies all three project actions`() = mcpTest { client ->
        val result = client.callTool(
            "get_guidelines",
            mapOf(
                "languages" to listOf("java"),
                "overrides" to listOf(
                    mapOf("rule" to "FX-REV-001", "action" to "waive", "reason" to "Solo project"),
                    mapOf("rule" to "FX-JAVA-002", "action" to "replace", "doc" to "docs/knowledge/errors.md"),
                    mapOf("rule" to "FX-JAVA-003", "action" to "extend", "doc" to "docs/knowledge/logging.md"),
                ),
            ),
        )

        result.ruleIds() shouldContainExactly listOf("FX-ARCH-001", "FX-JAVA-002", "FX-JAVA-003")
        result.rule("FX-JAVA-002").containsKey("body") shouldBe false
        result.rule("FX-JAVA-002")["projectOverride"]!!.jsonObject["action"]!!.jsonPrimitive.content shouldBe "replace"
        result.rule("FX-JAVA-003")["body"]!!.jsonPrimitive.content shouldBe "Log where handled."
        result.rule("FX-JAVA-003")["projectOverride"]!!.jsonObject["doc"]!!.jsonPrimitive.content shouldBe "docs/knowledge/logging.md"
    }

    @Test
    fun `warns about overrides that do not apply`() = mcpTest { client ->
        val result = client.callTool(
            "get_guidelines",
            mapOf(
                "team" to "payments",
                "languages" to listOf("java"),
                "overrides" to listOf(
                    mapOf("rule" to "FX-NOPE-001", "action" to "waive"),
                    mapOf("rule" to "FX-JAVA-001", "action" to "waive"),
                    mapOf("rule" to "FX-JAVA-002", "action" to "waive"),
                ),
            ),
        )

        result.isError shouldBe null
        result.warnings().map { it["type"]!!.jsonPrimitive.content } shouldContainExactly
            listOf("unknown_rule", "deprecated_rule", "not_in_effect")
    }

    @Test
    fun `draft rules are served only when configured`() {
        val payments = mapOf("team" to "payments")
        mcpTest { client -> client.callTool("get_guidelines", payments).ruleIds().contains("FX-PAYD-001") shouldBe false }
        mcpTest(includeDrafts = true) { client ->
            client.callTool("get_guidelines", payments).ruleIds().contains("FX-PAYD-001") shouldBe true
        }
    }

    @Test
    fun `invalid input is a tool error`() = mcpTest { client ->
        val result = client.callTool("get_guidelines", mapOf("overrides" to listOf(mapOf("rule" to "FX-JAVA-002", "action" to "drop"))))

        result.isError shouldBe true
        result.structuredContent!!["error"]!!.jsonPrimitive.content shouldBe "invalid_arguments"
    }
}
