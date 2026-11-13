package cloud.larn.knowledge.infrastructure.source

import cloud.larn.knowledge.application.KnowledgeRepository
import cloud.larn.knowledge.domain.KnowledgeBase
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the served knowledge base in an [AtomicReference]. The stateless MCP transport builds a server per
 * request, and each one reads the current value, so a later reload only needs [replace].
 */
class InMemoryKnowledgeRepository(initial: KnowledgeBase) : KnowledgeRepository {
    private val ref = AtomicReference(initial)

    override fun current(): KnowledgeBase = ref.get()

    fun replace(knowledgeBase: KnowledgeBase) {
        ref.set(knowledgeBase)
    }
}
