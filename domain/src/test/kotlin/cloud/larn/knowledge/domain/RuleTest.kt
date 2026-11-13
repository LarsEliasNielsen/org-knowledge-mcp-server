package cloud.larn.knowledge.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RuleTest {
    private val id = RuleId("JAVA-ERR-001")

    @Test
    fun `a deprecated rule may name its successor`() {
        val rule = Rule(id, "Old rule", "", status = RuleStatus.DEPRECATED, supersededBy = RuleId("JAVA-ERR-007"))

        rule.isActive shouldBe false
        rule.supersededBy shouldBe RuleId("JAVA-ERR-007")
    }

    @Test
    fun `an active rule cannot name a successor`() {
        shouldThrow<IllegalArgumentException> { Rule(id, "Rule", "", supersededBy = RuleId("JAVA-ERR-007")) }
    }

    @Test
    fun `a rule cannot supersede or override itself`() {
        shouldThrow<IllegalArgumentException> { Rule(id, "Rule", "", status = RuleStatus.DEPRECATED, supersededBy = id) }
        shouldThrow<IllegalArgumentException> { Rule(id, "Rule", "", overrides = listOf(id)) }
    }

    @Test
    fun `a rule needs a title`() {
        shouldThrow<IllegalArgumentException> { Rule(id, " ", "") }
    }

    @Test
    fun `parses a rule heading`() {
        RuleHeading.parse("JAVA-ERR-003 Never swallow exceptions") shouldBe
            RuleHeading(RuleId("JAVA-ERR-003"), "Never swallow exceptions")
        RuleHeading.parse("  ORG-SEC-API-120   Use TLS  ") shouldBe RuleHeading(RuleId("ORG-SEC-API-120"), "Use TLS")
    }

    @Test
    fun `headings without a leading rule id and title are not rules`() {
        listOf(
            "Never swallow exceptions",
            "JAVA-ERR-003",
            "JAVA-ERR-003:Never",
            "JAVA-ERR-03 Short number",
            "See JAVA-ERR-003 for details",
            "java-err-003 Lowercase",
        ).forEach { RuleHeading.parse(it).shouldBeNull() }
    }

    @Test
    fun `finds a section by slug`() {
        val errors = Section(Slug("errors"), "Errors", "## Errors\n")
        val doc = KnowledgeDoc(
            id = DocId("java-error-handling"),
            title = "Error handling in Java",
            kind = Kind.GUIDELINE,
            layer = Layer.LANGUAGE,
            scope = Scope(languages = setOf("java")),
            owner = "java-guild",
            status = DocStatus.ACTIVE,
            summary = "How we handle exceptions.",
            sections = listOf(errors),
            sourcePath = "languages/java/error-handling.md",
        )

        doc.section(Slug("errors")) shouldBe errors
        doc.section(Slug("logging")).shouldBeNull()
    }

    @Test
    fun `layers are ordered from least to most specific`() {
        Layer.entries.sorted() shouldBe listOf(Layer.ORG, Layer.LANGUAGE, Layer.TEAM)
    }
}
