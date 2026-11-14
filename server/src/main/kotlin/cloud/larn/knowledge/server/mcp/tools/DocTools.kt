package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.application.GetDoc
import cloud.larn.knowledge.application.GetDocResult
import cloud.larn.knowledge.application.ListDocs
import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeDoc
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val READ_ONLY = ToolAnnotations(readOnlyHint = true, openWorldHint = false)

/** `list_docs(kind?, owner?)`: the document catalogue with summaries. */
class ListDocsTool(private val listDocs: ListDocs) {
    val definition = Tool(
        name = NAME,
        title = "List knowledge documents",
        description = "Lists the organization's knowledge documents (guidelines, architecture docs, ADRs, library and " +
            "service docs) with id, kind, layer, scope, owner and summary. Use get_doc to read one.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("kind", stringProperty("Only documents of this kind.", Kind.entries.map(::lowercaseName)))
                put("owner", stringProperty("Only documents owned by this owner, e.g. java-guild."))
            },
        ),
        annotations = READ_ONLY,
    )

    fun call(arguments: JsonObject?): CallToolResult = toolCall {
        val args = ToolArguments(arguments, setOf("kind", "owner"))
        val kind = args.string("kind")?.let { value ->
            Kind.entries.firstOrNull { lowercaseName(it) == value.trim().lowercase() }
                ?: throw ToolInputException("Invalid kind '$value': expected one of ${Kind.entries.joinToString { lowercaseName(it) }}")
        }
        val docs = listDocs(kind, args.string("owner")?.trim())

        toolResult(render(docs), buildJsonObject { putJsonArray("documents") { docs.forEach { add(it.summaryJson()) } } })
    }

    private fun render(docs: List<KnowledgeDoc>): String {
        if (docs.isEmpty()) return "No knowledge documents match."
        return "Knowledge documents (${docs.size}):\n\n" + docs.joinToString("\n") { doc ->
            val status = if (doc.status == DocStatus.ACTIVE) "" else ", ${lowercaseName(doc.status)}"
            "- `${doc.id}` ${doc.title} (${lowercaseName(doc.kind)}, ${lowercaseName(doc.layer)} layer, " +
                "scope: ${doc.scope.describe()}$status): ${doc.summary}"
        }
    }

    companion object {
        const val NAME = "list_docs"
    }
}

/** `get_doc(id, section?)`: one document's Markdown, or one H2 section of it. */
class GetDocTool(private val getDoc: GetDoc) {
    val definition = Tool(
        name = NAME,
        title = "Get a knowledge document",
        description = "Returns the Markdown of one knowledge document, or of one H2 section when `section` is given " +
            "(slug such as logging-and-context, or the heading text). Deprecated rules appear with their status.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", stringProperty("Document id, e.g. java-error-handling (see list_docs)."))
                put("section", stringProperty("Optional H2 section: slug or heading text."))
            },
            required = listOf("id"),
        ),
        annotations = READ_ONLY,
    )

    fun call(arguments: JsonObject?): CallToolResult = toolCall {
        val args = ToolArguments(arguments, setOf("id", "section"))
        val rawId = args.requiredString("id").trim()
        val id = DocId.parseOrNull(rawId) ?: return@toolCall unknownDoc(rawId)

        when (val result = getDoc(id, args.string("section")?.takeIf { it.isNotBlank() })) {
            is GetDocResult.Found -> toolResult(
                "${result.doc.headerLine()}\n\n${result.doc.markdown.trim()}",
                docJson(result.doc, section = null, markdown = result.doc.markdown.trim()),
            )
            is GetDocResult.FoundSection -> toolResult(
                "${result.doc.headerLine()}\n\n${result.section.markdown}",
                docJson(result.doc, section = result.section.slug.value, markdown = result.section.markdown),
            )
            is GetDocResult.UnknownDoc -> unknownDoc(rawId)
            is GetDocResult.UnknownSection -> {
                val slugs = result.available.map { it.slug.value }
                val listing = if (slugs.isEmpty()) "It has no H2 sections." else "Available sections: ${slugs.joinToString()}."
                toolError("unknown_section", "Document '${result.doc.id}' has no section '${result.requested}'. $listing") {
                    put("id", result.doc.id.value)
                    put("availableSections", jsonStrings(slugs))
                }
            }
        }
    }

    private fun unknownDoc(id: String) =
        toolError("unknown_doc", "Unknown document '$id'. Use list_docs to see the available documents.") { put("id", id) }

    private fun docJson(doc: KnowledgeDoc, section: String?, markdown: String) = buildJsonObject {
        put("document", doc.summaryJson())
        put("related", jsonStrings(doc.related.map { it.value }))
        putJsonArray("sections") {
            doc.sections.forEach { addJsonObject { put("slug", it.slug.value); put("heading", it.heading) } }
        }
        putJsonArray("rules") {
            doc.rules.forEach { rule ->
                addJsonObject {
                    put("id", rule.id.value)
                    put("title", rule.title)
                    put("status", lowercaseName(rule.status))
                    rule.supersededBy?.let { put("supersededBy", it.value) }
                }
            }
        }
        section?.let { put("section", it) }
        put("markdown", markdown)
    }

    companion object {
        const val NAME = "get_doc"
    }
}
