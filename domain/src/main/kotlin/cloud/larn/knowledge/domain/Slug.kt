package cloud.larn.knowledge.domain

/** GitHub-style heading anchor: `## Logging and context` -> `logging-and-context`. */
@JvmInline
value class Slug(val value: String) {
    override fun toString(): String = value

    companion object {
        private val REMOVED = Regex("[^\\p{L}\\p{N}\\p{M}\\p{Pc} -]")

        /** Lowercases, drops punctuation (keeping letters, digits, `-` and `_`), and turns each space into `-`. */
        fun of(heading: String): Slug = Slug(heading.trim().lowercase().replace(REMOVED, "").replace(' ', '-'))
    }
}
