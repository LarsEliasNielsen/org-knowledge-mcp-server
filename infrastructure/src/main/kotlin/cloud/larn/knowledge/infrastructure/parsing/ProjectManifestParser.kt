package cloud.larn.knowledge.infrastructure.parsing

import cloud.larn.knowledge.application.KnowledgeError
import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.domain.ProjectContext
import cloud.larn.knowledge.domain.ProjectOverride
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.ScopeValue
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.Serializable

sealed interface ManifestResult {
    data class Success(val context: ProjectContext) : ManifestResult

    data class Failure(val errors: List<KnowledgeError>) : ManifestResult
}

/**
 * Parses a project's `.knowledge.yaml` into a [ProjectContext]. The server never reads manifests (agents pass them
 * to get_guidelines); this is for tests that keep examples in sync and for a future project-repo lint.
 */
object ProjectManifestParser {
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = true))

    fun parse(path: String, content: String): ManifestResult {
        val manifest = try {
            yaml.decodeFromString(ManifestYaml.serializer(), content)
        } catch (e: YamlException) {
            return ManifestResult.Failure(listOf(KnowledgeError(path, e.line, e.message)))
        }

        val errors = mutableListOf<String>()
        fun identifiers(key: String, values: List<String>) = values.filter { value ->
            ScopeValue.isValid(value).also { valid -> if (!valid) errors += "Invalid $key value '$value': expected a lowercase identifier" }
        }

        val team = manifest.team?.let { identifiers("team", listOf(it)).singleOrNull() }
        val languages = identifiers("languages", manifest.languages)
        val frameworks = identifiers("frameworks", manifest.frameworks)
        val overrides = manifest.overrides.mapIndexedNotNull { index, override ->
            val rule = RuleId.parseOrNull(override.rule)
            val action = OverrideAction.entries.firstOrNull { it.name.lowercase() == override.action }
            when {
                rule == null -> null.also { errors += "overrides[$index]: invalid rule id '${override.rule}'" }
                action == null -> null.also {
                    errors += "overrides[$index]: invalid action '${override.action}': expected one of extend, replace, waive"
                }
                action != OverrideAction.WAIVE && override.doc.isNullOrBlank() ->
                    null.also { errors += "overrides[$index]: '${override.action}' needs a project doc" }
                else -> ProjectOverride(rule, action, override.doc, override.reason)
            }
        }

        if (errors.isEmpty()) {
            try {
                return ManifestResult.Success(ProjectContext(team, languages, frameworks, overrides))
            } catch (e: IllegalArgumentException) {
                errors += e.message ?: "Invalid manifest"
            }
        }
        return ManifestResult.Failure(errors.map { KnowledgeError(path, null, it) })
    }

    @Serializable
    private data class ManifestYaml(
        val team: String? = null,
        val languages: List<String> = emptyList(),
        val frameworks: List<String> = emptyList(),
        val overrides: List<OverrideYaml> = emptyList(),
    )

    @Serializable
    private data class OverrideYaml(
        val rule: String,
        val action: String,
        val doc: String? = null,
        val reason: String? = null,
    )
}
