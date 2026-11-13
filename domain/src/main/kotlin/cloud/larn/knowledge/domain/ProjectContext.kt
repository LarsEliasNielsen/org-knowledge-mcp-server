package cloud.larn.knowledge.domain

/**
 * The context an agent passes for its project, mirroring `.knowledge.yaml`.
 * [languages] keeps its order: the language layer of resolved guidelines follows it.
 */
data class ProjectContext(
    val team: String? = null,
    val languages: List<String> = emptyList(),
    val frameworks: List<String> = emptyList(),
    val overrides: List<ProjectOverride> = emptyList(),
) {
    init {
        team?.let { ScopeValue.requireValid("team", it) }
        languages.forEach { ScopeValue.requireValid("language", it) }
        frameworks.forEach { ScopeValue.requireValid("framework", it) }
        require(languages.distinct().size == languages.size) { "Duplicate languages in $languages" }
        val duplicateRules = overrides.groupBy { it.rule }.filterValues { it.size > 1 }.keys
        require(duplicateRules.isEmpty()) { "More than one override for rule(s) ${duplicateRules.joinToString()}" }
    }

    fun overrideFor(rule: RuleId): ProjectOverride? = overrides.firstOrNull { it.rule == rule }
}

/** A project's decision about one shared rule. [doc] is the project-relative path of the project doc. */
data class ProjectOverride(
    val rule: RuleId,
    val action: OverrideAction,
    val doc: String? = null,
    val reason: String? = null,
) {
    init {
        if (action != OverrideAction.WAIVE) {
            require(!doc.isNullOrBlank()) { "Override of $rule with action $action needs a project doc" }
        }
    }
}
