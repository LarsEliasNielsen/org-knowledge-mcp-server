package cloud.larn.knowledge.application

/** A problem in the knowledge base, located by file path (relative to the knowledge root) and 1-based line. */
data class KnowledgeError(val path: String, val line: Int?, val message: String) {
    override fun toString(): String = if (line == null) "$path: $message" else "$path:$line: $message"
}
