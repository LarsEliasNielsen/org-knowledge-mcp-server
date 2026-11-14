package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.application.GetGuidelines
import cloud.larn.knowledge.application.KnowledgeRepository
import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.domain.ProjectContext
import cloud.larn.knowledge.domain.ProjectOverride
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.server.McpTestSupport
import cloud.larn.knowledge.server.McpTestSupport.text
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class GetGuidelinesToolTest {
    private val tool = GetGuidelinesTool(GetGuidelines(KnowledgeRepository { McpTestSupport.fixtureKnowledgeBase }))

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun parse(text: String) = GetGuidelinesTool.parseContext(json(text))

    private fun CallToolResult.errorMessage(): String {
        isError shouldBe true
        structuredContent!!["error"]!!.jsonPrimitive.content shouldBe "invalid_arguments"
        return text
    }

    // --- Input parsing ---

    @Test
    fun `parses the manifest fields, normalizing case and duplicates`() {
        parse(
            """
            {"team": " Payments ", "languages": ["Java", "kotlin", "java"], "frameworks": ["Spring-Boot"],
             "overrides": [
               {"rule": "java-err-003", "action": "Replace", "doc": "docs/knowledge/errors.md", "reason": "Legacy"},
               {"rule": "ORG-REV-001", "action": "waive"}
             ]}
            """,
        ) shouldBe ProjectContext(
            team = "payments",
            languages = listOf("java", "kotlin"),
            frameworks = listOf("spring-boot"),
            overrides = listOf(
                ProjectOverride(RuleId("JAVA-ERR-003"), OverrideAction.REPLACE, doc = "docs/knowledge/errors.md", reason = "Legacy"),
                ProjectOverride(RuleId("ORG-REV-001"), OverrideAction.WAIVE),
            ),
        )
    }

    @Test
    fun `no arguments is an empty context`() {
        GetGuidelinesTool.parseContext(null) shouldBe ProjectContext()
        parse("""{"team": null, "languages": []}""") shouldBe ProjectContext()
    }

    @Test
    fun `rejects invalid arguments with a precise message`() {
        fun error(arguments: String) = tool.call(json(arguments)).errorMessage()

        error("""{"language": ["java"]}""") shouldBe "Unknown argument(s) language; expected frameworks, languages, overrides, team"
        error("""{"languages": "java"}""") shouldBe "Argument 'languages' must be an array of strings"
        error("""{"languages": ["spring boot"]}""") shouldBe
            "Invalid languages value 'spring boot': expected an identifier such as java or spring-boot"
        error("""{"overrides": [{"rule": "JAVA-ERR-3", "action": "waive"}]}""") shouldBe
            "overrides[0]: invalid rule id 'JAVA-ERR-3', expected e.g. JAVA-ERR-003"
        error("""{"overrides": [{"rule": "JAVA-ERR-003", "action": "drop"}]}""") shouldBe
            "overrides[0]: invalid action 'drop', expected one of extend, replace, waive"
        error("""{"overrides": [{"rule": "JAVA-ERR-003"}]}""") shouldBe "overrides[0]: missing 'action'"
        error("""{"overrides": [{"rule": "JAVA-ERR-003", "action": "waive"}, {"rule": "JAVA-ERR-004", "action": "extend"}]}""") shouldBe
            "overrides[1]: 'extend' needs 'doc', the project doc that extends JAVA-ERR-004"
        error("""{"overrides": [{"rule": "JAVA-ERR-003", "action": "waive", "why": "x"}]}""") shouldBe
            "overrides[0]: Unknown argument(s) why; expected action, doc, reason, rule"
        error("""{"overrides": ["JAVA-ERR-003"]}""") shouldBe "Argument 'overrides' must be an array of objects"
        error("""{"overrides": [{"rule": "JAVA-ERR-003", "action": "waive"}, {"rule": "java-err-003", "action": "waive"}]}""") shouldBe
            "More than one override for rule JAVA-ERR-003"
    }

    @Test
    fun `is read-only and documents the override shape`() {
        tool.definition.annotations?.readOnlyHint shouldBe true
        val items = tool.definition.inputSchema.properties!!["overrides"]!!.jsonObject["items"]!!.jsonObject
        items["required"].toString() shouldBe """["rule","action"]"""
    }

    // --- Rendering ---

    private val representative = """
        {"team": "payments", "languages": ["kotlin", "java"],
         "overrides": [
           {"rule": "FX-ARCH-001", "action": "extend", "doc": "docs/knowledge/events.md", "reason": "Uses Kafka."},
           {"rule": "FX-JAVA-003", "action": "replace", "doc": "docs/knowledge/logging.md"},
           {"rule": "FX-REV-001", "action": "waive", "reason": "Solo project"},
           {"rule": "FX-JAVA-001", "action": "waive"}
         ]}
    """

    @Test
    fun `renders rules grouped by layer and language, with override markers and warnings`() {
        tool.call(json(representative)).text shouldBe """
            # Guidelines for team payments · languages: kotlin, java

            4 shared rules in effect, ordered org → language → team; a more specific layer wins. Rules marked as extended or replaced point to a project doc, which takes precedence: read it and apply it.

            ## Org layer

            ### FX-ARCH-001 Integrate across domains through events
            _from `adr-0001-events` (adr)_

            No synchronous calls into another domain's database.

            > **Extended by the project doc `docs/knowledge/events.md`**: Uses Kafka. Apply that doc on top of this rule.

            ## Language layer: kotlin

            ### FX-KT-001 No GlobalScope
            _from `kotlin-coroutines` (guideline)_

            Use structured concurrency.

            ## Language layer: java

            ### FX-JAVA-003 Log once
            _from `java-errors` (guideline)_

            > **Replaced by the project doc `docs/knowledge/logging.md`**. Follow that doc instead of this rule.

            ## Team layer

            ### FX-PAY-001 Translate gateway errors
            _from `pay-errors` (guideline) · overrides FX-JAVA-002_

            Translate at the adapter.

            ## Warnings about the project's overrides

            - Override of FX-JAVA-001: the rule is deprecated and superseded by FX-JAVA-002; FX-PAY-001 applies to this project in its place. Point the override at FX-PAY-001 or remove it.
        """.trimIndent()
    }

    @Test
    fun `a deprecation warning names the declared successor when it applies directly`() {
        val result = tool.call(json("""{"languages": ["java"], "overrides": [{"rule": "FX-JAVA-001", "action": "waive"}]}"""))

        result.text.lines().last() shouldBe
            "- Override of FX-JAVA-001: the rule is deprecated and superseded by FX-JAVA-002. Point the override at FX-JAVA-002 or remove it."
    }

    @Test
    fun `renders an empty context as org-wide rules without override hint`() {
        val text = tool.call(null).text

        text shouldStartWith "# Guidelines for any project (org-wide rules only)\n\n2 shared rules in effect"
        text.contains("takes precedence") shouldBe false
        text.contains("## Warnings") shouldBe false
    }

    @Test
    fun `returns rules and warnings as structured content`() {
        val structured = tool.call(json(representative)).structuredContent!!

        structured["context"] shouldBe json("""{"team": "payments", "languages": ["kotlin", "java"], "frameworks": []}""")
        structured["rules"] shouldBe json(
            """
            {"rules": [
              {"id": "FX-ARCH-001", "title": "Integrate across domains through events", "layer": "org",
               "source": {"id": "adr-0001-events", "title": "ADR-0001: Integrate through events", "kind": "adr"},
               "projectOverride": {"action": "extend", "doc": "docs/knowledge/events.md", "reason": "Uses Kafka."},
               "body": "No synchronous calls into another domain's database."},
              {"id": "FX-KT-001", "title": "No GlobalScope", "layer": "language", "language": "kotlin",
               "source": {"id": "kotlin-coroutines", "title": "Kotlin coroutines", "kind": "guideline"},
               "body": "Use structured concurrency."},
              {"id": "FX-JAVA-003", "title": "Log once", "layer": "language", "language": "java",
               "source": {"id": "java-errors", "title": "Java errors", "kind": "guideline"},
               "projectOverride": {"action": "replace", "doc": "docs/knowledge/logging.md"}},
              {"id": "FX-PAY-001", "title": "Translate gateway errors", "layer": "team",
               "source": {"id": "pay-errors", "title": "Payments errors", "kind": "guideline"},
               "replaces": ["FX-JAVA-002"], "body": "Translate at the adapter."}
            ]}
            """,
        )["rules"]
        structured["warnings"] shouldBe json(
            """
            {"warnings": [{"rule": "FX-JAVA-001", "type": "deprecated_rule", "supersededBy": "FX-JAVA-002", "successorInEffect": "FX-PAY-001",
              "message": "Override of FX-JAVA-001: the rule is deprecated and superseded by FX-JAVA-002; FX-PAY-001 applies to this project in its place. Point the override at FX-PAY-001 or remove it."}]}
            """,
        )["warnings"]
    }

    @Test
    fun `describes every warning type`() {
        val result = tool.call(
            json(
                """
                {"team": "payments", "languages": ["java"], "overrides": [
                  {"rule": "FX-NOPE-001", "action": "waive"},
                  {"rule": "FX-KT-001", "action": "waive"},
                  {"rule": "FX-JAVA-002", "action": "waive"},
                  {"rule": "FX-PAYD-001", "action": "waive"}
                ]}
                """,
            ),
        )

        result.text.substringAfter("## Warnings about the project's overrides\n\n") shouldBe """
            - Override of FX-NOPE-001: no such rule exists. Check the rule id in .knowledge.yaml.
            - Override of FX-KT-001: the rule does not apply to this project's team, languages or frameworks, so the override has no effect.
            - Override of FX-JAVA-002: FX-PAY-001 already replaces this rule for the project. Override FX-PAY-001 instead.
            - Override of FX-PAYD-001: the rule's document is a draft and not served, so the rule is not in effect.
        """.trimIndent()
        result.structuredContent!!["warnings"] shouldBe json(
            """
                {"w": [
                  {"rule": "FX-NOPE-001", "type": "unknown_rule", "message": "Override of FX-NOPE-001: no such rule exists. Check the rule id in .knowledge.yaml."},
                  {"rule": "FX-KT-001", "type": "not_in_effect", "reason": "out_of_scope", "message": "Override of FX-KT-001: the rule does not apply to this project's team, languages or frameworks, so the override has no effect."},
                  {"rule": "FX-JAVA-002", "type": "not_in_effect", "reason": "overridden_by", "overriddenBy": "FX-PAY-001", "message": "Override of FX-JAVA-002: FX-PAY-001 already replaces this rule for the project. Override FX-PAY-001 instead."},
                  {"rule": "FX-PAYD-001", "type": "not_in_effect", "reason": "document_not_served", "documentStatus": "draft", "message": "Override of FX-PAYD-001: the rule's document is a draft and not served, so the rule is not in effect."}
                ]}
            """,
        )["w"]
    }

    @Test
    fun `parse errors never reach the resolver`() {
        tool.call(JsonObject(mapOf("team" to Json.parseToJsonElement("1")))).errorMessage() shouldBe "Argument 'team' must be a string"
    }
}
