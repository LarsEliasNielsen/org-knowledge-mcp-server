package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.KnowledgeDoc

/** Which documents the tools may show. Drafts stay hidden unless [includeDrafts] is set (local preview). */
data class Visibility(val includeDrafts: Boolean = false) {
    fun isVisible(doc: KnowledgeDoc): Boolean = doc.status != DocStatus.DRAFT || includeDrafts
}
