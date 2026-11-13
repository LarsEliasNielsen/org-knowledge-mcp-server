package cloud.larn.knowledge.infrastructure.parsing

import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.Scope
import cloud.larn.knowledge.domain.ScopeValue

/** The layer and default scope a document gets from its folder. */
data class DocLocation(val layer: Layer, val defaultScope: Scope)

/**
 * Maps a path relative to the knowledge root to its layer:
 * `org/…` → org, `languages/<language>/…` → language, `teams/<team>/…` → team.
 */
object KnowledgePath {
    fun normalize(path: String): String = path.replace('\\', '/').removePrefix("./")

    fun locate(relativePath: String): Result<DocLocation> {
        val segments = normalize(relativePath).split('/')
        if (!segments.last().endsWith(".md")) return failure("Knowledge files must have the .md extension")

        return when (segments.first()) {
            "org" if segments.size >= 2 -> Result.success(DocLocation(Layer.ORG, Scope.EVERYONE))
            "languages" if segments.size >= 3 -> named(segments[1], "language") { Scope(languages = setOf(it)) }
                .map { DocLocation(Layer.LANGUAGE, it) }
            "teams" if segments.size >= 3 -> named(segments[1], "team") { Scope(teams = setOf(it)) }
                .map { DocLocation(Layer.TEAM, it) }
            else -> failure("File is outside the layer folders: expected org/, languages/<language>/ or teams/<team>/")
        }
    }

    private fun named(value: String, what: String, scope: (String) -> Scope): Result<Scope> =
        if (ScopeValue.isValid(value)) {
            Result.success(scope(value))
        } else {
            failure("Folder '$value' is not a valid $what name: expected a lowercase identifier")
        }

    private fun <T> failure(message: String): Result<T> = Result.failure(IllegalArgumentException(message))
}
