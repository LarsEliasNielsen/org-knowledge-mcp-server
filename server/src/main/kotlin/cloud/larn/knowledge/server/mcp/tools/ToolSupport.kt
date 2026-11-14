package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Scope
import cloud.larn.knowledge.domain.ScopeDimension
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Bad tool input; reported to the client as a tool error so the model can correct the call. */
class ToolInputException(message: String) : IllegalArgumentException(message)

/** Typed access to a tool call's arguments. Unknown argument names are rejected so typos surface. */
class ToolArguments(private val arguments: JsonObject?, allowed: Set<String>) {
    init {
        val unknown = arguments.orEmpty().keys - allowed
        if (unknown.isNotEmpty()) {
            throw ToolInputException(
                "Unknown argument(s) ${unknown.sorted().joinToString()}; expected ${allowed.sorted().joinToString()}",
            )
        }
    }

    fun string(name: String): String? = when (val value = arguments?.get(name)) {
        null, JsonNull -> null
        is JsonPrimitive if value.isString -> value.content
        else -> throw ToolInputException("Argument '$name' must be a string")
    }

    fun requiredString(name: String): String =
        string(name)?.takeIf { it.isNotBlank() } ?: throw ToolInputException("Missing required argument '$name'")

    fun stringList(name: String): List<String> = when (val value = arguments?.get(name)) {
        null, JsonNull -> emptyList()
        is JsonArray -> value.map {
            (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                ?: throw ToolInputException("Argument '$name' must be an array of strings")
        }
        else -> throw ToolInputException("Argument '$name' must be an array of strings")
    }

    fun objectList(name: String): List<JsonObject> = when (val value = arguments?.get(name)) {
        null, JsonNull -> emptyList()
        is JsonArray -> value.map { it as? JsonObject ?: throw ToolInputException("Argument '$name' must be an array of objects") }
        else -> throw ToolInputException("Argument '$name' must be an array of objects")
    }
}

/** Runs [block], turning bad input into a tool error result instead of a protocol error. */
inline fun toolCall(block: () -> CallToolResult): CallToolResult = try {
    block()
} catch (e: ToolInputException) {
    toolError("invalid_arguments", e.message!!)
}

fun toolResult(text: String, structured: JsonObject): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), structuredContent = structured)

fun toolError(code: String, message: String, details: JsonObjectBuilder.() -> Unit = {}): CallToolResult = CallToolResult(
    content = listOf(TextContent(message)),
    isError = true,
    structuredContent = buildJsonObject {
        put("error", code)
        put("message", message)
        details()
    },
)

/** JSON-schema fragment for a string property. */
fun stringProperty(description: String, enum: List<String>? = null): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    enum?.let { values -> putJsonArray("enum") { values.forEach(::add) } }
}

fun lowercaseName(value: Enum<*>): String = value.name.lowercase()

fun Scope.toJson(): JsonObject = buildJsonObject {
    ScopeDimension.entries.forEach { dimension ->
        val values = values(dimension)
        if (values.isNotEmpty()) putJsonArray(dimension.key) { values.sorted().forEach(::add) }
    }
}

/** `everyone`, or e.g. `languages: java; frameworks: spring-boot`. */
fun Scope.describe(): String = ScopeDimension.entries
    .filter { values(it).isNotEmpty() }
    .joinToString("; ") { "${it.key}: ${values(it).sorted().joinToString(", ")}" }
    .ifEmpty { "everyone" }

/** The catalogue fields of a document, shared by list_docs, get_doc and get_guidelines. */
fun KnowledgeDoc.summaryJson(): JsonObject = buildJsonObject {
    put("id", id.value)
    put("title", title)
    put("kind", lowercaseName(kind))
    put("layer", lowercaseName(layer))
    put("status", lowercaseName(status))
    put("scope", scope.toJson())
    put("owner", owner)
    put("summary", summary)
    lastReviewed?.let { put("lastReviewed", it.toString()) }
}

/** One-line provenance header: `> java-error-handling · guideline · language layer · active · scope: … · owner: …`. */
fun KnowledgeDoc.headerLine(): String =
    "> $id · ${lowercaseName(kind)} · ${lowercaseName(layer)} layer · ${lowercaseName(status)} · " +
        "scope: ${scope.describe()} · owner: $owner"

fun jsonStrings(values: Iterable<String>): JsonElement = buildJsonArray { values.forEach(::add) }
