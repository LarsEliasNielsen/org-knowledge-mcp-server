package cloud.larn.knowledge.infrastructure.parsing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Front matter as written. Enum-like fields stay strings so errors can name the allowed values. */
@Serializable
internal data class FrontMatterYaml(
    val id: String,
    val title: String,
    val kind: String,
    val scope: ScopeYaml? = null,
    val owner: String,
    val status: String,
    val summary: String,
    val related: List<String> = emptyList(),
    @SerialName("last-reviewed") val lastReviewed: String? = null,
)

/** `null` means the dimension is omitted and inherits the folder scope. */
@Serializable
internal data class ScopeYaml(
    val languages: List<String>? = null,
    val teams: List<String>? = null,
    val frameworks: List<String>? = null,
)

/** The fenced ```rule block directly under a rule heading. */
@Serializable
internal data class RuleBlockYaml(
    val status: String? = null,
    @SerialName("superseded-by") val supersededBy: String? = null,
    val overrides: List<String> = emptyList(),
)
