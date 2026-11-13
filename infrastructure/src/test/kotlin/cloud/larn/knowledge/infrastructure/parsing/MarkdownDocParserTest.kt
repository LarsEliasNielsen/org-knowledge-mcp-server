package cloud.larn.knowledge.infrastructure.parsing

import cloud.larn.knowledge.application.KnowledgeError
import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.RuleStatus
import cloud.larn.knowledge.domain.Scope
import cloud.larn.knowledge.domain.Slug
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import kotlin.test.Test

class MarkdownDocParserTest {
    private val parser = MarkdownDocParser()

    private fun fixture(path: String): String =
        checkNotNull(javaClass.getResource("/parsing/$path")) { "Missing fixture $path" }.readText()

    private fun parseFixture(path: String): KnowledgeDoc =
        parser.parse(path, fixture(path)).shouldBeInstanceOf<ParseResult.Success>().doc

    private fun errorsOf(path: String, content: String): List<KnowledgeError> =
        parser.parse(path, content).shouldBeInstanceOf<ParseResult.Failure>().errors

    private fun doc(frontMatter: String = VALID_FRONT_MATTER, body: String = "") = "---\n$frontMatter\n---\n$body"

    // --- Valid documents per layer ---

    @Test
    fun `parses an org doc with sections and rules`() {
        val doc = parseFixture("org/code-review.md")

        doc.id shouldBe DocId("org-code-review")
        doc.title shouldBe "Code review"
        doc.kind shouldBe Kind.GUIDELINE
        doc.layer shouldBe Layer.ORG
        doc.scope shouldBe Scope.EVERYONE
        doc.owner shouldBe "platform-guild"
        doc.status shouldBe DocStatus.ACTIVE
        doc.summary shouldBe "How we review code across the organization."
        doc.lastReviewed shouldBe LocalDate.of(2026, 11, 1)
        doc.sourcePath shouldBe "org/code-review.md"
        doc.sections.map { it.slug } shouldContainExactly listOf(Slug("reviewing"), Slug("merging"))
        doc.rules.map { it.id } shouldContainExactly listOf(RuleId("ORG-REV-001"))
    }

    @Test
    fun `parses a language doc and narrows its scope with front matter`() {
        val doc = parseFixture("languages/java/error-handling.md")

        doc.layer shouldBe Layer.LANGUAGE
        doc.scope shouldBe Scope(languages = setOf("java"), frameworks = setOf("spring-boot"))
        doc.related shouldContainExactly listOf(DocId("org-code-review"))
        doc.lastReviewed.shouldBeNull()
    }

    @Test
    fun `parses a team doc with a rule override`() {
        val doc = parseFixture("teams/payments/errors.md")

        doc.layer shouldBe Layer.TEAM
        doc.scope shouldBe Scope(languages = setOf("java"), teams = setOf("payments"))
        doc.status shouldBe DocStatus.DRAFT
        doc.rules.single().overrides shouldContainExactly listOf(RuleId("JAVA-ERR-003"))
    }

    @Test
    fun `accepts windows separators, CRLF line endings and a BOM`() {
        val content = "﻿" + fixture("org/code-review.md").replace("\n", "\r\n")

        val doc = parser.parse("org\\code-review.md", content).shouldBeInstanceOf<ParseResult.Success>().doc

        doc.sourcePath shouldBe "org/code-review.md"
        doc.rules.single().body shouldBe "Reviews block delivery, so they come first."
    }

    // --- Rules ---

    @Test
    fun `reads rule metadata from the rule block`() {
        val rules = parseFixture("languages/java/error-handling.md").rules

        rules.map { it.id } shouldContainExactly listOf(RuleId("JAVA-ERR-001"), RuleId("JAVA-ERR-003"))
        with(rules[0]) {
            title shouldBe "Wrap checked exceptions at module boundaries"
            status shouldBe RuleStatus.DEPRECATED
            supersededBy shouldBe RuleId("JAVA-ERR-003")
            body shouldBe "Old advice."
        }
    }

    @Test
    fun `a rule without a rule block is active and its body runs to the next H2`() {
        val rule = parseFixture("languages/java/error-handling.md").rules[1]

        rule.status shouldBe RuleStatus.ACTIVE
        rule.supersededBy.shouldBeNull()
        rule.overrides.shouldBeEmpty()
        rule.body shouldBe """
            Log or rethrow. Never both.

            ```java
            try { call(); } catch (IOException e) { throw new UncheckedIOException(e); }
            ```
        """.trimIndent()
    }

    @Test
    fun `a rule body ends at the next H3`() {
        parseFixture("org/code-review.md").rules.single().body shouldBe "Reviews block delivery, so they come first."
    }

    @Test
    fun `an H3 without a rule id is plain content`() {
        val doc = parseFixture("org/code-review.md")

        doc.rules shouldHaveSize 1
        doc.section(Slug("reviewing"))!!.markdown shouldContain "### Who reviews"
    }

    @Test
    fun `rule ids in deeper headings or lists are not rules`() {
        val body = """
            ## Notes

            #### JAVA-ERR-009 Too deep

            - ### JAVA-ERR-010 In a list
        """.trimIndent()

        parser.parse("org/a.md", doc(body = body)).shouldBeInstanceOf<ParseResult.Success>().doc.rules.shouldBeEmpty()
    }

    @Test
    fun `an empty rule block is allowed`() {
        val body = "### ORG-REV-002 Small PRs\n```rule\n```\nKeep them small."

        val rule = parser.parse("org/a.md", doc(body = body)).shouldBeInstanceOf<ParseResult.Success>().doc.rules.single()
        rule.body shouldBe "Keep them small."
    }

    @Test
    fun `reports invalid rule metadata`() {
        val body = """
            ### ORG-REV-002 Bad status
            ```rule
            status: retired
            ```
            ### ORG-REV-003 Bad ids
            ```rule
            superseded-by: nope
            overrides: [also-bad]
            ```
            ### ORG-REV-004 Successor on an active rule
            ```rule
            superseded-by: ORG-REV-005
            ```
        """.trimIndent()

        errorsOf("org/a.md", doc(body = body)) shouldContainExactly listOf(
            KnowledgeError("org/a.md", 11, "Rule ORG-REV-002: invalid status 'retired': expected one of active, deprecated"),
            KnowledgeError("org/a.md", 15, "Rule ORG-REV-003: invalid superseded-by 'nope'"),
            KnowledgeError("org/a.md", 15, "Rule ORG-REV-003: invalid overrides entry 'also-bad'"),
            KnowledgeError("org/a.md", 20, "Rule ORG-REV-004 sets superseded-by but is not deprecated"),
        )
    }

    @Test
    fun `reports unknown keys in a rule block`() {
        val body = "### ORG-REV-002 Rule\n```rule\nowner: me\n```\n"

        val error = errorsOf("org/a.md", doc(body = body)).single()
        error.line shouldBe 12
        error.message shouldContain "Rule block of ORG-REV-002"
        error.message shouldContain "owner"
    }

    @Test
    fun `reports a rule block that does not follow a rule heading`() {
        val body = "## Section\n\n```rule\nstatus: active\n```\n"

        errorsOf("org/a.md", doc(body = body)) shouldContainExactly listOf(
            KnowledgeError("org/a.md", 12, "A rule block must directly follow a rule heading (### <RULE-ID> <title>)"),
        )
    }

    // --- Sections ---

    @Test
    fun `sections return the exact source text up to the next H2`() {
        val doc = parseFixture("languages/java/error-handling.md")
        val source = fixture("languages/java/error-handling.md")

        val exceptions = doc.section(Slug("exceptions"))!!
        exceptions.heading shouldBe "Exceptions"
        exceptions.markdown shouldBe source.substringAfter("---\n## ").let { "## " + it.substringBefore("\n\n## Logging") }
        doc.section(Slug("logging-and-context"))!!.markdown shouldBe "## Logging and context\n\nInclude the correlation id."
    }

    @Test
    fun `content before the first H2 is not a section but stays in the document markdown`() {
        val doc = parseFixture("org/code-review.md")

        doc.sections.none { it.markdown.contains("Applies to every repository") } shouldBe true
        doc.markdown shouldContain "Applies to every repository."
        doc.markdown.startsWith("# Code review") shouldBe true
    }

    @Test
    fun `heading text keeps inline code and drops formatting`() {
        val body = "## Using `Result` *types*\n"

        val section = parser.parse("org/a.md", doc(body = body)).shouldBeInstanceOf<ParseResult.Success>().doc.sections.single()
        section.heading shouldBe "Using Result types"
        section.slug shouldBe Slug("using-result-types")
    }

    // --- Front matter ---

    @Test
    fun `reports missing front matter`() {
        errorsOf("org/a.md", "# No front matter\n") shouldContainExactly listOf(
            KnowledgeError("org/a.md", 1, "Missing front matter: the file must start with a '---' line and close it with another '---' line"),
        )
        errorsOf("org/a.md", "---\nid: a\n") shouldHaveSize 1
    }

    @Test
    fun `reports a missing required key`() {
        val error = errorsOf("org/a.md", doc(VALID_FRONT_MATTER.replace("owner: platform-guild\n", ""))).single()

        error.message shouldContain "Front matter"
        error.message shouldContain "owner"
    }

    @Test
    fun `reports an unknown key with its line`() {
        val error = errorsOf("org/a.md", doc("$VALID_FRONT_MATTER\noverrides: []")).single()

        error.line shouldBe 9
        error.message shouldContain "overrides"
    }

    @Test
    fun `reports malformed yaml`() {
        val error = errorsOf("org/a.md", doc("id: [unclosed\ntitle: x")).single()

        error.message shouldContain "Front matter"
    }

    @Test
    fun `reports every invalid value with the line of its key`() {
        val frontMatter = """
            id: Not_Kebab
            title: " "
            kind: howto
            owner: platform-guild
            status: retired
            summary: Fine.
            related: [ok-doc, Bad]
            last-reviewed: 2026-13-01
        """.trimIndent()

        errorsOf("org/a.md", doc(frontMatter)) shouldContainExactly listOf(
            KnowledgeError("org/a.md", 2, "Invalid id 'Not_Kebab': expected lowercase kebab-case"),
            KnowledgeError("org/a.md", 3, "'title' must not be blank"),
            KnowledgeError("org/a.md", 4, "Invalid kind 'howto': expected one of guideline, architecture, adr, library, service"),
            KnowledgeError("org/a.md", 6, "Invalid status 'retired': expected one of draft, active, deprecated"),
            KnowledgeError("org/a.md", 8, "Invalid related doc id 'Bad'"),
            KnowledgeError("org/a.md", 9, "Invalid last-reviewed date '2026-13-01': expected YYYY-MM-DD"),
        )
    }

    @Test
    fun `reports a scope that widens the folder scope`() {
        val errors = errorsOf("languages/java/a.md", doc("$VALID_FRONT_MATTER\nscope: { languages: [java, go] }"))

        errors shouldContainExactly listOf(
            KnowledgeError("languages/java/a.md", 9, "scope.languages widens the folder scope: go not in java"),
        )
    }

    @Test
    fun `reports empty and invalid scope dimensions`() {
        val errors = errorsOf("org/a.md", doc("$VALID_FRONT_MATTER\nscope: { languages: [], frameworks: [Spring] }"))

        errors.map { it.message } shouldContainExactly listOf(
            "scope.languages must not be empty: omit it to inherit the folder scope",
            "Invalid scope.frameworks value 'Spring': expected a lowercase identifier",
        )
    }

    // --- Paths ---

    @Test
    fun `reports files outside the layer folders`() {
        listOf("misc/a.md", "a.md", "languages/a.md", "teams/a.md").forEach { path ->
            errorsOf(path, doc()).single().message shouldContain "outside the layer folders"
        }
    }

    @Test
    fun `reports invalid layer folder names and non-markdown files`() {
        errorsOf("languages/Java/a.md", doc()).single().message shouldBe
            "Folder 'Java' is not a valid language name: expected a lowercase identifier"
        errorsOf("org/a.txt", doc()).single().message shouldBe "Knowledge files must have the .md extension"
    }

    @Test
    fun `collects path, front matter and body errors together`() {
        val errors = errorsOf("misc/a.md", doc(VALID_FRONT_MATTER.replace("kind: guideline", "kind: x"), "```rule\n```"))

        errors shouldHaveSize 3
    }

    private companion object {
        val VALID_FRONT_MATTER = """
            id: a-doc
            title: A doc
            kind: guideline
            owner: platform-guild
            status: active
            summary: A summary.
        """.trimIndent() + "\n"
    }
}
