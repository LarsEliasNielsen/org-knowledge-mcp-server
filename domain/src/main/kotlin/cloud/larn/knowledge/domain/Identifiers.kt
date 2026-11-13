package cloud.larn.knowledge.domain

/** Stable id of a knowledge document, kebab-case (`java-error-handling`, `adr-0012-idempotency-keys`). */
@JvmInline
value class DocId(val value: String) {
    init {
        require(PATTERN.matches(value)) { "Invalid doc id '$value': expected lowercase kebab-case" }
    }

    override fun toString(): String = value

    companion object {
        val PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

        fun parseOrNull(value: String): DocId? = if (PATTERN.matches(value)) DocId(value) else null
    }
}

/** Stable, never-reused id of a rule: uppercase prefix segments plus a three-digit number (`JAVA-ERR-003`). */
@JvmInline
value class RuleId(val value: String) : Comparable<RuleId> {
    init {
        require(PATTERN.matches(value)) { "Invalid rule id '$value': expected e.g. JAVA-ERR-003" }
    }

    override fun compareTo(other: RuleId): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        const val PATTERN_SOURCE = "[A-Z]+(?:-[A-Z]+)*-\\d{3}"
        val PATTERN = Regex("^$PATTERN_SOURCE$")

        fun parseOrNull(value: String): RuleId? = if (PATTERN.matches(value)) RuleId(value) else null
    }
}

/**
 * Lowercase identifier used for languages, teams and frameworks in scopes and project contexts
 * (`java`, `payments`, `spring-boot`, `c++`, `c#`). Adapters normalize user input before constructing one.
 */
object ScopeValue {
    val PATTERN = Regex("^[a-z0-9][a-z0-9+#._-]*$")

    fun isValid(value: String): Boolean = PATTERN.matches(value)

    internal fun requireValid(dimension: String, value: String) {
        require(isValid(value)) { "Invalid $dimension '$value': expected a lowercase identifier" }
    }
}
