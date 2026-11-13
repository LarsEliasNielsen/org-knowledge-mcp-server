package cloud.larn.knowledge.domain

/**
 * A validated, immutable set of knowledge documents with lookups by id.
 * Construct it only from documents that passed validation; it rejects duplicate doc and rule ids.
 */
class KnowledgeBase(docs: Collection<KnowledgeDoc>) {
    /** All documents, drafts included, sorted by id. */
    val docs: List<KnowledgeDoc> = docs.sortedBy { it.id.value }

    private val docsById: Map<DocId, KnowledgeDoc> = this.docs.associateBy { it.id }
    private val rulesById: Map<RuleId, RuleRef> =
        this.docs.flatMap { doc -> doc.rules.map { RuleRef(it, doc) } }.associateBy { it.rule.id }

    init {
        require(docsById.size == this.docs.size) { "Duplicate doc ids" }
        require(rulesById.size == this.docs.sumOf { it.rules.size }) { "Duplicate rule ids" }
    }

    fun doc(id: DocId): KnowledgeDoc? = docsById[id]

    fun rule(id: RuleId): RuleRef? = rulesById[id]

    companion object {
        val EMPTY = KnowledgeBase(emptyList())
    }
}

/** A rule together with the document that defines it. */
data class RuleRef(val rule: Rule, val doc: KnowledgeDoc)
