package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.domain.KnowledgeDoc

/** Reads and parses knowledge files from wherever they are stored. */
fun interface KnowledgeSource {
    fun load(): SourceLoad
}

/** The documents that parsed, plus every error from files that did not. */
data class SourceLoad(val docs: List<KnowledgeDoc>, val errors: List<KnowledgeError>)

/** Holds the knowledge base the use cases currently serve. */
fun interface KnowledgeRepository {
    fun current(): KnowledgeBase
}
