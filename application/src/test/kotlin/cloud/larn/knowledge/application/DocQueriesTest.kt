package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.Scope
import cloud.larn.knowledge.domain.Section
import cloud.larn.knowledge.domain.Slug
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DocQueriesTest {
    private val logging = Section(Slug("logging-and-context"), "Logging and context", "## Logging and context\n\nText.")
    private val errors = Section(Slug("errors"), "Errors", "## Errors\n\nText.")

    private val docs = listOf(
        doc("team-a", Layer.TEAM, owner = "payments-team"),
        doc("org-b", Layer.ORG),
        doc("org-a", Layer.ORG, kind = Kind.ADR, status = DocStatus.DEPRECATED),
        doc("java-a", Layer.LANGUAGE, owner = "java-guild", sections = listOf(errors, logging)),
        doc("team-draft", Layer.TEAM, status = DocStatus.DRAFT),
    )
    private val repository = KnowledgeRepository { KnowledgeBase(docs) }

    private fun ids(result: List<KnowledgeDoc>) = result.map { it.id.value }

    @Test
    fun `lists visible docs by layer then id, deprecated included`() {
        ids(ListDocs(repository)()) shouldContainExactly listOf("org-a", "org-b", "java-a", "team-a")
    }

    @Test
    fun `lists drafts when visible`() {
        ids(ListDocs(repository, Visibility(includeDrafts = true))()) shouldContainExactly
            listOf("org-a", "org-b", "java-a", "team-a", "team-draft")
    }

    @Test
    fun `filters by kind and owner`() {
        ids(ListDocs(repository)(kind = Kind.ADR)) shouldContainExactly listOf("org-a")
        ids(ListDocs(repository)(owner = "java-guild")) shouldContainExactly listOf("java-a")
        ids(ListDocs(repository)(kind = Kind.GUIDELINE, owner = "payments-team")) shouldContainExactly listOf("team-a")
        ListDocs(repository)(kind = Kind.SERVICE).shouldBeEmpty()
    }

    @Test
    fun `gets a whole document`() {
        GetDoc(repository)(DocId("java-a")) shouldBe GetDocResult.Found(docs[3])
    }

    @Test
    fun `gets a section by slug or by heading text`() {
        GetDoc(repository)(DocId("java-a"), "logging-and-context") shouldBe GetDocResult.FoundSection(docs[3], logging)
        GetDoc(repository)(DocId("java-a"), "Logging and context") shouldBe GetDocResult.FoundSection(docs[3], logging)
        GetDoc(repository)(DocId("java-a"), " errors ") shouldBe GetDocResult.FoundSection(docs[3], errors)
    }

    @Test
    fun `reports an unknown section with the available ones`() {
        GetDoc(repository)(DocId("java-a"), "nope") shouldBe GetDocResult.UnknownSection(docs[3], "nope", listOf(errors, logging))
    }

    @Test
    fun `unknown and hidden draft docs are unknown`() {
        GetDoc(repository)(DocId("missing")) shouldBe GetDocResult.UnknownDoc(DocId("missing"))
        GetDoc(repository)(DocId("team-draft")) shouldBe GetDocResult.UnknownDoc(DocId("team-draft"))
        GetDoc(repository, Visibility(includeDrafts = true))(DocId("team-draft")) shouldBe GetDocResult.Found(docs[4])
    }

    private fun doc(
        id: String,
        layer: Layer,
        kind: Kind = Kind.GUIDELINE,
        status: DocStatus = DocStatus.ACTIVE,
        owner: String = "owner",
        sections: List<Section> = emptyList(),
    ) = KnowledgeDoc(
        id = DocId(id),
        title = id,
        kind = kind,
        layer = layer,
        scope = Scope.EVERYONE,
        owner = owner,
        status = status,
        summary = "Summary of $id.",
        sections = sections,
        sourcePath = "$id.md",
    )
}
