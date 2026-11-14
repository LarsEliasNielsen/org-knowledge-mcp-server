package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.domain.ProjectContext
import cloud.larn.knowledge.domain.ProjectOverride
import cloud.larn.knowledge.domain.Rule
import cloud.larn.knowledge.domain.RuleId

/** The rules in effect for a project, in layer order, plus problems with the project's overrides. */
data class Guidelines(val rules: List<EffectiveRule>, val warnings: List<GuidelineWarning>)

/**
 * A shared rule in effect for the context.
 * [replaces] lists lower-layer rules this rule overrides that were dropped for this context.
 * [language] is the requested language that put a language-layer rule in effect (null for other layers).
 * [projectOverride] is set when the project extends or replaces the rule.
 */
data class EffectiveRule(
    val rule: Rule,
    val doc: KnowledgeDoc,
    val language: String? = null,
    val replaces: List<RuleId> = emptyList(),
    val projectOverride: ProjectOverride? = null,
) {
    val layer: Layer get() = doc.layer

    /** The kind of document the rule comes from: a guideline, an architecture doc, an ADR, ... */
    val kind: Kind get() = doc.kind
    val isReplacedByProject: Boolean get() = projectOverride?.action == OverrideAction.REPLACE
    val isExtendedByProject: Boolean get() = projectOverride?.action == OverrideAction.EXTEND
}

/** A project override that does not apply as written. The agent should tell the user so the manifest gets fixed. */
sealed interface GuidelineWarning {
    val rule: RuleId

    /** No rule with this id exists. */
    data class UnknownRule(override val rule: RuleId) : GuidelineWarning

    /**
     * The rule is deprecated, so it is no longer in effect. [supersededBy] is its declared successor;
     * [successorInEffect] is the rule that actually applies to this project in its place, following further
     * deprecations and higher-layer overrides (null when no successor applies). Target that rule instead.
     */
    data class DeprecatedRule(
        override val rule: RuleId,
        val supersededBy: RuleId?,
        val successorInEffect: RuleId?,
    ) : GuidelineWarning

    /** The rule exists but is not in effect for this context, so the override does nothing. */
    data class RuleNotInEffect(override val rule: RuleId, val reason: NotInEffectReason) : GuidelineWarning
}

sealed interface NotInEffectReason {
    /** The rule's document does not apply to the project's team, languages or frameworks. */
    data object OutOfScope : NotInEffectReason

    /** The rule's document is a draft (and drafts are hidden) or deprecated. */
    data class DocumentNotServed(val status: DocStatus) : NotInEffectReason

    /** A higher-layer rule already replaces it for this context; override that rule instead. */
    data class OverriddenBy(val rule: RuleId) : NotInEffectReason
}

/**
 * Resolves the shared layers for a project context. Rules are the unit, whatever the document kind: a binding
 * constraint in an architecture doc or ADR is served like a coding guideline; the surrounding prose is not.
 * 1. active docs of any kind (drafts only when visible) whose scope matches the context,
 * 2. their active rules (deprecated rules dropped),
 * 3. rules overridden by an in-effect higher-layer rule dropped,
 * 4. project overrides applied: `waive` drops the rule, `replace` and `extend` mark it with the project doc
 *    (waiving an overriding rule does not bring back the rule it replaced),
 * 5. ordered by layer, then (language layer) the request's language order, then doc id, then position in the doc.
 */
class GetGuidelines(
    private val repository: KnowledgeRepository,
    private val visibility: Visibility = Visibility(),
) {
    operator fun invoke(context: ProjectContext): Guidelines {
        val knowledgeBase = repository.current()
        val served = knowledgeBase.docs.filter { isServed(it) && it.scope.matches(context) }
        val candidates = served.flatMap { doc -> doc.rules.filter(Rule::isActive).map { doc to it } }

        val candidateIds = candidates.map { (_, rule) -> rule.id }.toSet()
        // Target -> the first in-scope rule that overrides it. Overrides chain: if a team rule replaces a
        // language rule that itself replaced an org rule, both lower rules stay dropped.
        val replacedBy: Map<RuleId, RuleId> = candidates
            .flatMap { (_, rule) -> rule.overrides.map { target -> target to rule.id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, overriders) -> overriders.first() }
        val inEffect = candidates.filter { (_, rule) -> rule.id !in replacedBy }
        val inEffectIds = inEffect.map { (_, rule) -> rule.id }.toSet()

        val rules = inEffect
            .mapNotNull { (doc, rule) ->
                val projectOverride = context.overrideFor(rule.id)
                if (projectOverride?.action == OverrideAction.WAIVE) return@mapNotNull null
                EffectiveRule(
                    rule = rule,
                    doc = doc,
                    language = if (doc.layer == Layer.LANGUAGE) context.languages.firstOrNull { it in doc.scope.languages } else null,
                    replaces = rule.overrides.filter { it in candidateIds },
                    projectOverride = projectOverride,
                )
            }
            .sortedWith(order(context))

        val warnings = context.overrides
            .filter { it.rule !in inEffectIds }
            .map { warningFor(it.rule, knowledgeBase, context, replacedBy, inEffectIds) }

        return Guidelines(rules, warnings)
    }

    private fun isServed(doc: KnowledgeDoc): Boolean =
        doc.status != DocStatus.DEPRECATED && visibility.isVisible(doc)

    private fun order(context: ProjectContext): Comparator<EffectiveRule> = compareBy<EffectiveRule>(
        { it.layer },
        { rule -> rule.language?.let(context.languages::indexOf) ?: 0 },
        { it.doc.id.value },
        { rule -> rule.doc.rules.indexOf(rule.rule) },
    )

    private fun warningFor(
        id: RuleId,
        knowledgeBase: KnowledgeBase,
        context: ProjectContext,
        replacedBy: Map<RuleId, RuleId>,
        inEffectIds: Set<RuleId>,
    ): GuidelineWarning {
        val (rule, doc) = knowledgeBase.rule(id) ?: return GuidelineWarning.UnknownRule(id)
        return when {
            !rule.isActive -> GuidelineWarning.DeprecatedRule(
                id,
                rule.supersededBy,
                rule.supersededBy?.let { successorInEffect(it, knowledgeBase, replacedBy, inEffectIds) },
            )
            !isServed(doc) -> GuidelineWarning.RuleNotInEffect(id, NotInEffectReason.DocumentNotServed(doc.status))
            !doc.scope.matches(context) -> GuidelineWarning.RuleNotInEffect(id, NotInEffectReason.OutOfScope)
            else -> GuidelineWarning.RuleNotInEffect(id, NotInEffectReason.OverriddenBy(replacedBy.getValue(id)))
        }
    }

    /**
     * Follows [start] to the rule in effect for the context: through higher-layer rules that replace it and
     * through `superseded-by` of deprecated rules. Null when the chain ends at a rule that does not apply.
     */
    private fun successorInEffect(
        start: RuleId,
        knowledgeBase: KnowledgeBase,
        replacedBy: Map<RuleId, RuleId>,
        inEffectIds: Set<RuleId>,
    ): RuleId? {
        val visited = mutableSetOf<RuleId>()
        var current: RuleId? = start
        while (current != null && visited.add(current)) {
            if (current in inEffectIds) return current
            current = replacedBy[current] ?: knowledgeBase.rule(current)?.rule?.takeUnless { it.isActive }?.supersededBy
        }
        return null
    }
}
