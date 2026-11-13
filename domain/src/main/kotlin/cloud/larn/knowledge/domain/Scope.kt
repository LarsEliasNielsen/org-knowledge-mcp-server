package cloud.larn.knowledge.domain

/** One of the three dimensions a scope can restrict. */
enum class ScopeDimension(val key: String) {
    LANGUAGES("languages"),
    TEAMS("teams"),
    FRAMEWORKS("frameworks"),
}

/**
 * Who a document applies to. An empty dimension matches anything; values within a dimension are ORed,
 * dimensions are ANDed.
 */
data class Scope(
    val languages: Set<String> = emptySet(),
    val teams: Set<String> = emptySet(),
    val frameworks: Set<String> = emptySet(),
) {
    init {
        ScopeDimension.entries.forEach { dimension ->
            values(dimension).forEach { ScopeValue.requireValid(dimension.key, it) }
        }
    }

    fun values(dimension: ScopeDimension): Set<String> = when (dimension) {
        ScopeDimension.LANGUAGES -> languages
        ScopeDimension.TEAMS -> teams
        ScopeDimension.FRAMEWORKS -> frameworks
    }

    fun matches(context: ProjectContext): Boolean =
        matches(ScopeDimension.LANGUAGES, context.languages) &&
            matches(ScopeDimension.TEAMS, listOfNotNull(context.team)) &&
            matches(ScopeDimension.FRAMEWORKS, context.frameworks)

    private fun matches(dimension: ScopeDimension, contextValues: Collection<String>): Boolean {
        val required = values(dimension)
        return required.isEmpty() || contextValues.any { it in required }
    }

    /**
     * Applies a front-matter scope to this folder-default scope. Dimensions [narrowing] leaves empty inherit
     * the default. A non-empty dimension must be a subset of the default's (when the default restricts it),
     * otherwise the front matter would widen the scope.
     */
    fun refineWith(narrowing: Scope): ScopeRefinement {
        val widened = ScopeDimension.entries.mapNotNull { dimension ->
            val default = values(dimension)
            val outside = if (default.isEmpty()) emptySet() else narrowing.values(dimension) - default
            if (outside.isEmpty()) null else ScopeWidening(dimension, outside, default)
        }
        if (widened.isNotEmpty()) return ScopeRefinement.Widened(widened)

        fun pick(dimension: ScopeDimension) = narrowing.values(dimension).ifEmpty { values(dimension) }
        return ScopeRefinement.Narrowed(
            Scope(
                languages = pick(ScopeDimension.LANGUAGES),
                teams = pick(ScopeDimension.TEAMS),
                frameworks = pick(ScopeDimension.FRAMEWORKS),
            ),
        )
    }

    companion object {
        val EVERYONE = Scope()
    }
}

sealed interface ScopeRefinement {
    data class Narrowed(val scope: Scope) : ScopeRefinement

    data class Widened(val violations: List<ScopeWidening>) : ScopeRefinement
}

/** Values a front-matter scope adds to [dimension] beyond what the folder default [allowed]. */
data class ScopeWidening(val dimension: ScopeDimension, val outside: Set<String>, val allowed: Set<String>)
