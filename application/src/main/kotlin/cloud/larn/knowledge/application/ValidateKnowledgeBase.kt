package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.RuleId

sealed interface ValidationResult {
    data class Valid(val knowledgeBase: KnowledgeBase) : ValidationResult

    /** Every problem found, sorted by path and line. */
    data class Invalid(val errors: List<KnowledgeError>) : ValidationResult
}

/**
 * Loads the knowledge base and checks it as a whole: parse errors from the source, plus duplicate ids,
 * duplicate section slugs, unresolvable `related`, `superseded-by` and `overrides`, and overrides that do not
 * target a lower layer. Used both by the server at startup and by the build's content test.
 */
class ValidateKnowledgeBase(private val source: KnowledgeSource) {
    operator fun invoke(): ValidationResult {
        val loaded = source.load()
        val errors = loaded.errors + crossDocumentErrors(loaded.docs)
        return if (errors.isEmpty()) {
            ValidationResult.Valid(KnowledgeBase(loaded.docs))
        } else {
            ValidationResult.Invalid(errors.sortedWith(compareBy({ it.path }, { it.line ?: 0 }, { it.message })))
        }
    }

    private fun crossDocumentErrors(docs: List<KnowledgeDoc>): List<KnowledgeError> {
        val docsById = docs.groupBy { it.id }
        val rules = docs.flatMap { doc -> doc.rules.map { doc to it } }
        val rulesById = rules.groupBy { (_, rule) -> rule.id }
        val errors = mutableListOf<KnowledgeError>()

        docsById.filterValues { it.size > 1 }.forEach { (id, duplicates) ->
            duplicates.forEach { doc ->
                val others = duplicates.filter { it !== doc }.joinToString { it.sourcePath }
                errors += KnowledgeError(doc.sourcePath, null, "Duplicate doc id '$id' (also in $others)")
            }
        }

        rulesById.filterValues { it.size > 1 }.forEach { (id, duplicates) ->
            duplicates.forEach { (doc, rule) ->
                val others = duplicates.filter { it.second !== rule }.joinToString { (d, r) -> location(d.sourcePath, r.line) }
                errors += KnowledgeError(doc.sourcePath, rule.line, "Duplicate rule id $id (also at $others)")
            }
        }

        docs.forEach { doc ->
            doc.sections.groupBy { it.slug }.filterValues { it.size > 1 }.forEach { (slug, sections) ->
                sections.drop(1).forEach { section ->
                    errors += KnowledgeError(
                        doc.sourcePath,
                        section.line,
                        "Duplicate section slug '$slug': heading '${section.heading}' clashes with " +
                            "'${sections.first().heading}'${sections.first().line?.let { " at line $it" } ?: ""}",
                    )
                }
            }

            doc.related.filterNot(docsById::containsKey).forEach {
                errors += KnowledgeError(doc.sourcePath, null, "Related doc '$it' does not exist")
            }
        }

        fun ruleRef(id: RuleId) = rulesById[id]?.singleOrNull()

        rules.forEach { (doc, rule) ->
            rule.supersededBy?.takeIf { it !in rulesById }?.let {
                errors += KnowledgeError(doc.sourcePath, rule.line, "Rule ${rule.id} is superseded by unknown rule $it")
            }
            rule.overrides.forEach { target ->
                val targetRef = ruleRef(target)
                when {
                    target !in rulesById ->
                        errors += KnowledgeError(doc.sourcePath, rule.line, "Rule ${rule.id} overrides unknown rule $target")
                    targetRef != null && targetRef.first.layer >= doc.layer ->
                        errors += KnowledgeError(
                            doc.sourcePath,
                            rule.line,
                            "Rule ${rule.id} (${doc.layer.label}) overrides $target (${targetRef.first.layer.label}): " +
                                "overrides must target a lower layer",
                        )
                }
            }
        }
        return errors
    }

    private fun location(path: String, line: Int?) = if (line == null) path else "$path:$line"

    private val Layer.label get() = "${name.lowercase()} layer"
}
