package cloud.larn.knowledge.server.mcp.tools

import cloud.larn.knowledge.application.EffectiveRule
import cloud.larn.knowledge.application.GetGuidelines
import cloud.larn.knowledge.application.GuidelineWarning
import cloud.larn.knowledge.application.Guidelines
import cloud.larn.knowledge.application.NotInEffectReason
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.domain.ProjectContext
import cloud.larn.knowledge.domain.ProjectOverride
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.ScopeValue
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `get_guidelines(team?, languages[], frameworks[], overrides[])`: the shared rules in effect for a project,
 * taking the contents of its `.knowledge.yaml`.
 */
class GetGuidelinesTool(private val getGuidelines: GetGuidelines) {
    val definition = Tool(
        name = NAME,
        title = "Get the guidelines for a project",
        description = "Returns the organization's rules in effect for a project: org-wide rules, then rules for each " +
            "language, then team rules; a more specific layer wins. Pass the contents of the project's .knowledge.yaml " +
            "(team, languages, frameworks, overrides). Rules the project extends or replaces point to a project doc, " +
            "which takes precedence: read it from the project repo and apply it. Warnings flag overrides that do not apply.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("team", stringProperty("Team that owns the project, e.g. payments."))
                put("languages", stringArrayProperty("Languages the project uses, most important first, e.g. [java, kotlin]."))
                put("frameworks", stringArrayProperty("Frameworks the project uses, e.g. [spring-boot]."))
                putJsonObject("overrides") {
                    put("type", "array")
                    put("description", "The project's overrides of shared rules, from .knowledge.yaml.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            put("rule", stringProperty("Rule id, e.g. JAVA-ERR-003."))
                            put("action", stringProperty("What the project does with the rule.", OverrideAction.entries.map(::lowercaseName)))
                            put("doc", stringProperty("Project-relative path of the project doc. Required for extend and replace."))
                            put("reason", stringProperty("Why the project overrides the rule."))
                        }
                        put("required", jsonStrings(listOf("rule", "action")))
                        put("additionalProperties", false)
                    }
                }
            },
        ),
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    fun call(arguments: JsonObject?): CallToolResult = toolCall {
        val context = parseContext(arguments)
        val guidelines = getGuidelines(context)
        toolResult(GuidelinesMarkdown.render(context, guidelines), structured(context, guidelines))
    }

    companion object {
        const val NAME = "get_guidelines"

        /** Builds a [ProjectContext] from tool arguments, lowercasing identifiers and uppercasing rule ids. */
        fun parseContext(arguments: JsonObject?): ProjectContext {
            val args = ToolArguments(arguments, setOf("team", "languages", "frameworks", "overrides"))
            val team = args.string("team")?.let { identifier("team", it) }
            val languages = args.stringList("languages").map { identifier("languages", it) }.distinct()
            val frameworks = args.stringList("frameworks").map { identifier("frameworks", it) }.distinct()
            val overrides = args.objectList("overrides").mapIndexed(::parseOverride)

            val duplicates = overrides.groupBy { it.rule }.filterValues { it.size > 1 }.keys
            if (duplicates.isNotEmpty()) {
                throw ToolInputException("More than one override for rule ${duplicates.joinToString()}")
            }
            return ProjectContext(team, languages, frameworks, overrides)
        }

        private fun identifier(argument: String, value: String): String {
            val normalized = value.trim().lowercase()
            if (!ScopeValue.isValid(normalized)) {
                throw ToolInputException("Invalid $argument value '$value': expected an identifier such as java or spring-boot")
            }
            return normalized
        }

        private fun parseOverride(index: Int, json: JsonObject): ProjectOverride {
            val where = "overrides[$index]"
            val args = try {
                ToolArguments(json, setOf("rule", "action", "doc", "reason"))
            } catch (e: ToolInputException) {
                throw ToolInputException("$where: ${e.message}")
            }
            fun required(name: String) = args.string(name)?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw ToolInputException("$where: missing '$name'")

            val ruleText = required("rule")
            val rule = RuleId.parseOrNull(ruleText.uppercase())
                ?: throw ToolInputException("$where: invalid rule id '$ruleText', expected e.g. JAVA-ERR-003")
            val actionText = required("action")
            val action = OverrideAction.entries.firstOrNull { lowercaseName(it) == actionText.lowercase() }
                ?: throw ToolInputException(
                    "$where: invalid action '$actionText', expected one of ${OverrideAction.entries.joinToString { lowercaseName(it) }}",
                )
            val doc = args.string("doc")?.trim()?.takeIf { it.isNotEmpty() }
            if (action != OverrideAction.WAIVE && doc == null) {
                throw ToolInputException("$where: '${lowercaseName(action)}' needs 'doc', the project doc that ${lowercaseName(action)}s $rule")
            }
            return ProjectOverride(rule, action, doc, args.string("reason")?.trim()?.takeIf { it.isNotEmpty() })
        }

        private fun stringArrayProperty(description: String) = buildJsonObject {
            put("type", "array")
            put("description", description)
            putJsonObject("items") { put("type", "string") }
        }

        fun structured(context: ProjectContext, guidelines: Guidelines): JsonObject = buildJsonObject {
            putJsonObject("context") {
                context.team?.let { put("team", it) }
                put("languages", jsonStrings(context.languages))
                put("frameworks", jsonStrings(context.frameworks))
            }
            putJsonArray("rules") {
                guidelines.rules.forEach { effective -> addJsonObject { ruleJson(effective) } }
            }
            putJsonArray("warnings") {
                guidelines.warnings.forEach { warning -> addJsonObject { warningJson(warning) } }
            }
        }

        private fun JsonObjectBuilder.ruleJson(effective: EffectiveRule) {
            val rule = effective.rule
            put("id", rule.id.value)
            put("title", rule.title)
            put("layer", lowercaseName(effective.layer))
            effective.language?.let { put("language", it) }
            putJsonObject("source") {
                put("id", effective.doc.id.value)
                put("title", effective.doc.title)
                put("kind", lowercaseName(effective.kind))
            }
            if (effective.replaces.isNotEmpty()) put("replaces", jsonStrings(effective.replaces.map { it.value }))
            effective.projectOverride?.let { override ->
                putJsonObject("projectOverride") {
                    put("action", lowercaseName(override.action))
                    override.doc?.let { put("doc", it) }
                    override.reason?.let { put("reason", it) }
                }
            }
            if (!effective.isReplacedByProject) put("body", rule.body)
        }

        private fun JsonObjectBuilder.warningJson(warning: GuidelineWarning) {
            put("rule", warning.rule.value)
            when (warning) {
                is GuidelineWarning.UnknownRule -> put("type", "unknown_rule")
                is GuidelineWarning.DeprecatedRule -> {
                    put("type", "deprecated_rule")
                    warning.supersededBy?.let { put("supersededBy", it.value) }
                    warning.successorInEffect?.let { put("successorInEffect", it.value) }
                }
                is GuidelineWarning.RuleNotInEffect -> {
                    put("type", "not_in_effect")
                    when (val reason = warning.reason) {
                        NotInEffectReason.OutOfScope -> put("reason", "out_of_scope")
                        is NotInEffectReason.DocumentNotServed -> {
                            put("reason", "document_not_served")
                            put("documentStatus", lowercaseName(reason.status))
                        }
                        is NotInEffectReason.OverriddenBy -> {
                            put("reason", "overridden_by")
                            put("overriddenBy", reason.rule.value)
                        }
                    }
                }
            }
            put("message", GuidelinesMarkdown.message(warning))
        }
    }
}

/** Renders resolved guidelines as Markdown for the model, grouped by layer (and by language within the language layer). */
object GuidelinesMarkdown {
    fun render(context: ProjectContext, guidelines: Guidelines): String = buildString {
        appendLine("# Guidelines for ${describe(context)}")
        appendLine()
        append("${guidelines.rules.size} shared rules in effect, ordered org → language → team; a more specific layer wins.")
        if (guidelines.rules.any { it.projectOverride != null }) {
            append(" Rules marked as extended or replaced point to a project doc, which takes precedence: read it and apply it.")
        }
        appendLine()

        guidelines.rules.groupBy { it.layer to it.language }.forEach { (group, rules) ->
            val (layer, language) = group
            appendLine()
            appendLine(if (layer == Layer.LANGUAGE && language != null) "## Language layer: $language" else "## ${layer.title} layer")
            rules.forEach { appendRule(it) }
        }

        if (guidelines.warnings.isNotEmpty()) {
            appendLine()
            appendLine("## Warnings about the project's overrides")
            appendLine()
            guidelines.warnings.forEach { appendLine("- ${message(it)}") }
        }
    }.trimEnd()

    private fun StringBuilder.appendRule(effective: EffectiveRule) {
        val rule = effective.rule
        val provenance = buildList {
            add("from `${effective.doc.id}` (${lowercaseName(effective.kind)})")
            if (effective.replaces.isNotEmpty()) add("overrides ${effective.replaces.joinToString()}")
        }.joinToString(" · ")

        appendLine()
        appendLine("### ${rule.id} ${rule.title}")
        appendLine("_${provenance}_")
        val override = effective.projectOverride
        when {
            override?.action == OverrideAction.REPLACE -> {
                appendLine()
                appendLine("> **Replaced by the project doc `${override.doc}`**${override.reason?.let { ": ${it.trimEnd('.')}" } ?: ""}. Follow that doc instead of this rule.")
            }
            else -> {
                if (rule.body.isNotBlank()) {
                    appendLine()
                    appendLine(rule.body)
                }
                if (override?.action == OverrideAction.EXTEND) {
                    appendLine()
                    appendLine("> **Extended by the project doc `${override.doc}`**${override.reason?.let { ": ${it.trimEnd('.')}" } ?: ""}. Apply that doc on top of this rule.")
                }
            }
        }
    }

    fun message(warning: GuidelineWarning): String {
        val subject = "Override of ${warning.rule}"
        return when (warning) {
            is GuidelineWarning.UnknownRule -> "$subject: no such rule exists. Check the rule id in .knowledge.yaml."
            is GuidelineWarning.DeprecatedRule -> deprecationMessage(subject, warning)
            is GuidelineWarning.RuleNotInEffect -> when (val reason = warning.reason) {
                NotInEffectReason.OutOfScope ->
                    "$subject: the rule does not apply to this project's team, languages or frameworks, so the override has no effect."
                is NotInEffectReason.DocumentNotServed ->
                    "$subject: the rule's document is ${lowercaseName(reason.status)}, so the rule is not in effect."
                is NotInEffectReason.OverriddenBy ->
                    "$subject: ${reason.rule} already replaces this rule for the project. Override ${reason.rule} instead."
            }
        }
    }

    private fun deprecationMessage(subject: String, warning: GuidelineWarning.DeprecatedRule): String {
        val successor = warning.supersededBy ?: return "$subject: the rule is deprecated. Remove the override."
        val target = warning.successorInEffect
        return when (target) {
            null -> "$subject: the rule is deprecated and superseded by $successor, which does not apply to this project. " +
                "Remove the override."
            successor -> "$subject: the rule is deprecated and superseded by $successor. Point the override at $successor or remove it."
            else -> "$subject: the rule is deprecated and superseded by $successor; $target applies to this project in its place. " +
                "Point the override at $target or remove it."
        }
    }

    private fun describe(context: ProjectContext): String {
        val parts = buildList {
            context.team?.let { add("team $it") }
            if (context.languages.isNotEmpty()) add("languages: ${context.languages.joinToString(", ")}")
            if (context.frameworks.isNotEmpty()) add("frameworks: ${context.frameworks.joinToString(", ")}")
        }
        return if (parts.isEmpty()) "any project (org-wide rules only)" else parts.joinToString(" · ")
    }

    private val Layer.title get() = lowercaseName(this).replaceFirstChar(Char::uppercase)
}
