package cloud.larn.knowledge.domain

import java.time.LocalDate

/** A Markdown knowledge document after parsing. Cross-document consistency is checked by validation, not here. */
data class KnowledgeDoc(
    val id: DocId,
    val title: String,
    val kind: Kind,
    val layer: Layer,
    /** Effective scope: the folder default refined by front matter. */
    val scope: Scope,
    val owner: String,
    val status: DocStatus,
    val summary: String,
    val related: List<DocId> = emptyList(),
    val lastReviewed: LocalDate? = null,
    val sections: List<Section> = emptyList(),
    val rules: List<Rule> = emptyList(),
    /** Path relative to the knowledge root, for error messages and provenance. */
    val sourcePath: String,
    /** The document body without front matter. */
    val markdown: String = "",
) {
    fun section(slug: Slug): Section? = sections.firstOrNull { it.slug == slug }
}

/**
 * An H2 section of a document, addressable by [slug]. [markdown] is the source text, heading included.
 * [line] is the 1-based line of the heading in the source file.
 */
data class Section(val slug: Slug, val heading: String, val markdown: String, val line: Int? = null)

/** A rule: an H3 headed by its id. [body] is the source Markdown below the heading, rule block excluded. */
data class Rule(
    val id: RuleId,
    val title: String,
    val body: String,
    val status: RuleStatus = RuleStatus.ACTIVE,
    val supersededBy: RuleId? = null,
    /** Lower-layer rules this rule replaces for contexts where it applies. */
    val overrides: List<RuleId> = emptyList(),
    /** 1-based line of the rule heading in the source file. */
    val line: Int? = null,
) {
    init {
        require(title.isNotBlank()) { "Rule $id has a blank title" }
        require(supersededBy == null || status == RuleStatus.DEPRECATED) {
            "Rule $id sets superseded-by but is not deprecated"
        }
        require(supersededBy != id) { "Rule $id cannot supersede itself" }
        require(id !in overrides) { "Rule $id cannot override itself" }
    }

    val isActive: Boolean get() = status == RuleStatus.ACTIVE
}

/** The id and title encoded in a rule heading: `### JAVA-ERR-003 Never swallow exceptions`. */
data class RuleHeading(val id: RuleId, val title: String) {
    companion object {
        private val HEADING = Regex("^(${RuleId.PATTERN_SOURCE})\\s+(\\S.*)$")

        /** Returns null for headings that don't start with a rule id followed by a title. */
        fun parse(text: String): RuleHeading? {
            val match = HEADING.matchEntire(text.trim()) ?: return null
            return RuleHeading(RuleId(match.groupValues[1]), match.groupValues[2].trim())
        }
    }
}
