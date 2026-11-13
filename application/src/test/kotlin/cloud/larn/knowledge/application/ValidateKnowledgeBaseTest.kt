package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.Rule
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.RuleStatus
import cloud.larn.knowledge.domain.Scope
import cloud.larn.knowledge.domain.Section
import cloud.larn.knowledge.domain.Slug
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class ValidateKnowledgeBaseTest {
    private fun validate(docs: List<KnowledgeDoc>, parseErrors: List<KnowledgeError> = emptyList()) =
        ValidateKnowledgeBase { SourceLoad(docs, parseErrors) }()

    private fun errorsOf(vararg docs: KnowledgeDoc): List<KnowledgeError> =
        validate(docs.toList()).shouldBeInstanceOf<ValidationResult.Invalid>().errors

    private fun shouldBeValid(vararg docs: KnowledgeDoc) {
        validate(docs.toList()).shouldBeInstanceOf<ValidationResult.Valid>().knowledgeBase.docs.size shouldBe docs.size
    }

    @Test
    fun `a consistent knowledge base is valid`() {
        shouldBeValid(
            doc("org-a", Layer.ORG, rules = listOf(rule("ORG-A-001")), related = listOf("java-b")),
            doc(
                "java-b",
                Layer.LANGUAGE,
                rules = listOf(
                    rule("JAVA-B-001", status = RuleStatus.DEPRECATED, supersededBy = "JAVA-B-002"),
                    rule("JAVA-B-002", overrides = listOf("ORG-A-001")),
                ),
            ),
            doc("team-c", Layer.TEAM, rules = listOf(rule("TEAM-C-001", overrides = listOf("JAVA-B-002", "ORG-A-001")))),
        )
    }

    @Test
    fun `an empty knowledge base is valid`() {
        shouldBeValid()
    }

    @Test
    fun `passes parse errors through, sorted by path and line`() {
        val errors = listOf(
            KnowledgeError("org/b.md", 3, "late"),
            KnowledgeError("org/a.md", 9, "second"),
            KnowledgeError("org/a.md", null, "first"),
        )

        validate(emptyList(), errors).shouldBeInstanceOf<ValidationResult.Invalid>().errors shouldContainExactly listOf(
            KnowledgeError("org/a.md", null, "first"),
            KnowledgeError("org/a.md", 9, "second"),
            KnowledgeError("org/b.md", 3, "late"),
        )
    }

    @Test
    fun `reports duplicate doc ids in every file`() {
        errorsOf(doc("org-a", Layer.ORG, path = "org/a.md"), doc("org-a", Layer.ORG, path = "org/b.md")) shouldContainExactly listOf(
            KnowledgeError("org/a.md", null, "Duplicate doc id 'org-a' (also in org/b.md)"),
            KnowledgeError("org/b.md", null, "Duplicate doc id 'org-a' (also in org/a.md)"),
        )
    }

    @Test
    fun `reports duplicate rule ids across and within documents`() {
        errorsOf(
            doc("org-a", Layer.ORG, path = "org/a.md", rules = listOf(rule("ORG-A-001", line = 5), rule("ORG-A-001", line = 9))),
            doc("org-b", Layer.ORG, path = "org/b.md", rules = listOf(rule("ORG-A-001", line = 2))),
        ) shouldContainExactly listOf(
            KnowledgeError("org/a.md", 5, "Duplicate rule id ORG-A-001 (also at org/a.md:9, org/b.md:2)"),
            KnowledgeError("org/a.md", 9, "Duplicate rule id ORG-A-001 (also at org/a.md:5, org/b.md:2)"),
            KnowledgeError("org/b.md", 2, "Duplicate rule id ORG-A-001 (also at org/a.md:5, org/a.md:9)"),
        )
    }

    @Test
    fun `reports duplicate section slugs within a document`() {
        val sections = listOf(
            Section(Slug("errors"), "Errors", "## Errors", line = 3),
            Section(Slug("errors"), "Errors!", "## Errors!", line = 8),
            Section(Slug("logging"), "Logging", "## Logging", line = 12),
        )

        errorsOf(doc("org-a", Layer.ORG, path = "org/a.md", sections = sections)) shouldContainExactly listOf(
            KnowledgeError("org/a.md", 8, "Duplicate section slug 'errors': heading 'Errors!' clashes with 'Errors' at line 3"),
        )
    }

    @Test
    fun `the same slug in different documents is fine`() {
        val section = Section(Slug("errors"), "Errors", "## Errors")
        shouldBeValid(doc("org-a", Layer.ORG, sections = listOf(section)), doc("org-b", Layer.ORG, sections = listOf(section)))
    }

    @Test
    fun `reports unknown related docs`() {
        errorsOf(doc("org-a", Layer.ORG, path = "org/a.md", related = listOf("missing-doc"))) shouldContainExactly listOf(
            KnowledgeError("org/a.md", null, "Related doc 'missing-doc' does not exist"),
        )
    }

    @Test
    fun `reports an unknown superseded-by rule`() {
        val deprecated = rule("ORG-A-001", status = RuleStatus.DEPRECATED, supersededBy = "ORG-A-099", line = 4)

        errorsOf(doc("org-a", Layer.ORG, path = "org/a.md", rules = listOf(deprecated))) shouldContainExactly listOf(
            KnowledgeError("org/a.md", 4, "Rule ORG-A-001 is superseded by unknown rule ORG-A-099"),
        )
    }

    @Test
    fun `reports an override of an unknown rule`() {
        errorsOf(
            doc("team-a", Layer.TEAM, path = "teams/x/a.md", rules = listOf(rule("TEAM-A-001", overrides = listOf("JAVA-X-001"), line = 6))),
        ) shouldContainExactly listOf(
            KnowledgeError("teams/x/a.md", 6, "Rule TEAM-A-001 overrides unknown rule JAVA-X-001"),
        )
    }

    @Test
    fun `reports overrides of rules in the same or a higher layer`() {
        errorsOf(
            doc("org-a", Layer.ORG, path = "org/a.md", rules = listOf(rule("ORG-A-001", overrides = listOf("JAVA-B-001"), line = 3))),
            doc("java-b", Layer.LANGUAGE, path = "languages/java/b.md", rules = listOf(rule("JAVA-B-001"), rule("JAVA-B-002", overrides = listOf("JAVA-B-001"), line = 7))),
        ) shouldContainExactly listOf(
            KnowledgeError("languages/java/b.md", 7, "Rule JAVA-B-002 (language layer) overrides JAVA-B-001 (language layer): overrides must target a lower layer"),
            KnowledgeError("org/a.md", 3, "Rule ORG-A-001 (org layer) overrides JAVA-B-001 (language layer): overrides must target a lower layer"),
        )
    }

    @Test
    fun `reports every problem at once`() {
        errorsOf(
            doc("org-a", Layer.ORG, path = "org/a.md", related = listOf("nope"), rules = listOf(rule("ORG-A-001", overrides = listOf("X-Y-001")))),
            doc("org-a", Layer.ORG, path = "org/b.md"),
        ).size shouldBe 4
    }

    private fun doc(
        id: String,
        layer: Layer,
        path: String = "$id.md",
        rules: List<Rule> = emptyList(),
        sections: List<Section> = emptyList(),
        related: List<String> = emptyList(),
    ) = KnowledgeDoc(
        id = DocId(id),
        title = id,
        kind = Kind.GUIDELINE,
        layer = layer,
        scope = Scope.EVERYONE,
        owner = "owner",
        status = DocStatus.ACTIVE,
        summary = "Summary of $id.",
        related = related.map(::DocId),
        sections = sections,
        rules = rules,
        sourcePath = path,
    )

    private fun rule(
        id: String,
        status: RuleStatus = RuleStatus.ACTIVE,
        supersededBy: String? = null,
        overrides: List<String> = emptyList(),
        line: Int? = null,
    ) = Rule(
        id = RuleId(id),
        title = "Rule $id",
        body = "Body of $id.",
        status = status,
        supersededBy = supersededBy?.let(::RuleId),
        overrides = overrides.map(::RuleId),
        line = line,
    )
}
