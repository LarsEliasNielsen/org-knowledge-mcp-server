package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.application.GetDoc
import cloud.larn.knowledge.application.KnowledgeRepository
import cloud.larn.knowledge.application.ListDocs
import cloud.larn.knowledge.application.Visibility
import cloud.larn.knowledge.server.McpTestSupport
import cloud.larn.knowledge.server.McpTestSupport.text
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

class DocToolsTest {
    private val repository = KnowledgeRepository { McpTestSupport.fixtureKnowledgeBase }
    private val listDocs = ListDocsTool(ListDocs(repository))
    private val getDoc = GetDocTool(GetDoc(repository))

    private fun args(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    private fun CallToolResult.documentIds() =
        structuredContent!!["documents"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private fun CallToolResult.errorCode() = structuredContent!!["error"]!!.jsonPrimitive.content

    // --- list_docs ---

    @Test
    fun `tools are read-only and closed-world`() {
        listOf(listDocs.definition, getDoc.definition).forEach {
            it.annotations?.readOnlyHint shouldBe true
            it.annotations?.openWorldHint shouldBe false
        }
        getDoc.definition.inputSchema.required shouldBe listOf("id")
    }

    @Test
    fun `lists visible documents by layer then id`() {
        val result = listDocs.call(null)

        result.isError shouldBe null
        result.documentIds() shouldContainExactly listOf("adr-0001-events", "org-review", "java-errors", "kotlin-coroutines", "pay-errors")
        result.text shouldStartWith "Knowledge documents (5):"
        result.text shouldContain "- `java-errors` Java errors (guideline, language layer, scope: languages: java): Fixture Java guideline."
        result.text shouldContain "- `pay-errors` Payments errors (guideline, team layer, scope: languages: java; teams: payments): Fixture team guideline."
    }

    @Test
    fun `returns catalogue fields as structured content`() {
        val review = listDocs.call(null).structuredContent!!["documents"]!!.jsonArray[1]

        review shouldBe buildJsonObject {
            put("id", "org-review")
            put("title", "Review")
            put("kind", "guideline")
            put("layer", "org")
            put("status", "active")
            putJsonObject("scope") {}
            put("owner", "leads")
            put("summary", "Fixture org guideline.")
            put("lastReviewed", "2026-11-01")
        }
    }

    @Test
    fun `filters by kind, case-insensitively, and by owner`() {
        listDocs.call(args("kind" to "ADR")).documentIds() shouldContainExactly listOf("adr-0001-events")
        listDocs.call(args("owner" to "payments-team")).documentIds() shouldContainExactly listOf("pay-errors")
        listDocs.call(args("kind" to "service")).text shouldBe "No knowledge documents match."
    }

    @Test
    fun `shows drafts only when visible`() {
        ListDocsTool(ListDocs(repository, Visibility(includeDrafts = true))).call(null).text shouldContain
            "- `pay-draft` Payments draft (guideline, team layer, scope: teams: payments, draft): Fixture draft."
    }

    @Test
    fun `rejects invalid list_docs arguments`() {
        with(listDocs.call(args("kind" to "howto"))) {
            isError shouldBe true
            errorCode() shouldBe "invalid_arguments"
            text shouldBe "Invalid kind 'howto': expected one of guideline, architecture, adr, library, service"
        }
        listDocs.call(args("team" to "payments")).text shouldBe "Unknown argument(s) team; expected kind, owner"
        listDocs.call(buildJsonObject { put("kind", 3) }).text shouldBe "Argument 'kind' must be a string"
    }

    // --- get_doc ---

    @Test
    fun `returns a whole document with a provenance header`() {
        val result = getDoc.call(args("id" to "java-errors"))

        result.isError shouldBe null
        result.text.lines().first() shouldBe "> java-errors · guideline · language layer · active · scope: languages: java · owner: java-guild"
        result.text shouldContain "## Exceptions\n\n### FX-JAVA-001 Old rule\n```rule\nstatus: deprecated"
        result.text shouldContain "### FX-JAVA-003 Log once\nLog where handled."
    }

    @Test
    fun `describes sections and rules, deprecated ones with their status`() {
        val structured = getDoc.call(args("id" to "java-errors")).structuredContent!!

        structured["document"]!!.jsonObject["id"]!!.jsonPrimitive.content shouldBe "java-errors"
        structured["sections"] shouldBe buildJsonArray {
            add(buildJsonObject { put("slug", "exceptions"); put("heading", "Exceptions") })
            add(buildJsonObject { put("slug", "logging-and-context"); put("heading", "Logging and context") })
        }
        structured["rules"]!!.jsonArray[0] shouldBe buildJsonObject {
            put("id", "FX-JAVA-001")
            put("title", "Old rule")
            put("status", "deprecated")
            put("supersededBy", "FX-JAVA-002")
        }
        structured.containsKey("section") shouldBe false
    }

    @Test
    fun `returns one section by slug or heading text`() {
        listOf("logging-and-context", "Logging and context").forEach { section ->
            val result = getDoc.call(args("id" to "java-errors", "section" to section))

            result.text shouldBe "> java-errors · guideline · language layer · active · scope: languages: java · owner: java-guild\n\n" +
                "## Logging and context\n\n### FX-JAVA-003 Log once\nLog where handled."
            result.structuredContent!!["section"]!!.jsonPrimitive.content shouldBe "logging-and-context"
        }
    }

    @Test
    fun `an unknown section lists the available ones`() {
        with(getDoc.call(args("id" to "java-errors", "section" to "nope"))) {
            isError shouldBe true
            errorCode() shouldBe "unknown_section"
            text shouldBe "Document 'java-errors' has no section 'nope'. Available sections: exceptions, logging-and-context."
            structuredContent!!["availableSections"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainExactly
                listOf("exceptions", "logging-and-context")
        }
    }

    @Test
    fun `unknown and malformed ids are unknown documents`() {
        listOf("missing-doc", "Java Errors").forEach { id ->
            with(getDoc.call(args("id" to id))) {
                isError shouldBe true
                errorCode() shouldBe "unknown_doc"
                text shouldBe "Unknown document '$id'. Use list_docs to see the available documents."
            }
        }
    }

    @Test
    fun `a hidden draft is reported as a draft, and served when drafts are visible`() {
        with(getDoc.call(args("id" to "pay-draft"))) {
            isError shouldBe true
            errorCode() shouldBe "draft_not_served"
            text shouldBe "Document 'pay-draft' is a draft and not served. Drafts are served only when the server runs with INCLUDE_DRAFTS=true."
        }
        GetDocTool(GetDoc(repository, Visibility(includeDrafts = true))).call(args("id" to "pay-draft")).isError shouldBe null
    }

    @Test
    fun `get_doc requires an id`() {
        getDoc.call(null).text shouldBe "Missing required argument 'id'"
        getDoc.call(args("id" to " ")).errorCode() shouldBe "invalid_arguments"
    }
}
