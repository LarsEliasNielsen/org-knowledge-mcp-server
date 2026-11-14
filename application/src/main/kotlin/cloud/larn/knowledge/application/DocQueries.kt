package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Section
import cloud.larn.knowledge.domain.Slug

/** The catalogue of visible documents, ordered by layer then id, optionally filtered by kind and owner. */
class ListDocs(
    private val repository: KnowledgeRepository,
    private val visibility: Visibility = Visibility(),
) {
    operator fun invoke(kind: Kind? = null, owner: String? = null): List<KnowledgeDoc> =
        repository.current().docs
            .filter { visibility.isVisible(it) }
            .filter { kind == null || it.kind == kind }
            .filter { owner == null || it.owner == owner }
            .sortedWith(compareBy({ it.layer }, { it.id.value }))
}

sealed interface GetDocResult {
    data class Found(val doc: KnowledgeDoc) : GetDocResult

    data class FoundSection(val doc: KnowledgeDoc, val section: Section) : GetDocResult

    /** No document has this id. */
    data class UnknownDoc(val id: DocId) : GetDocResult

    /** The document is a draft and drafts are not served, the same reason get_guidelines gives for its rules. */
    data class DraftNotServed(val id: DocId) : GetDocResult

    /** The document exists but has no H2 section matching the request; [available] lists its sections. */
    data class UnknownSection(val doc: KnowledgeDoc, val requested: String, val available: List<Section>) : GetDocResult
}

/** One document, or one of its H2 sections. */
class GetDoc(
    private val repository: KnowledgeRepository,
    private val visibility: Visibility = Visibility(),
) {
    /**
     * [section] may be a slug (`logging-and-context`) or the heading text (`Logging and context`),
     * which is turned into its slug.
     */
    operator fun invoke(id: DocId, section: String? = null): GetDocResult {
        val doc = repository.current().doc(id) ?: return GetDocResult.UnknownDoc(id)
        if (!visibility.isVisible(doc)) return GetDocResult.DraftNotServed(id)
        if (section == null) return GetDocResult.Found(doc)

        val match = doc.section(Slug(section.trim())) ?: doc.section(Slug.of(section))
        return match?.let { GetDocResult.FoundSection(doc, it) } ?: GetDocResult.UnknownSection(doc, section, doc.sections)
    }
}
