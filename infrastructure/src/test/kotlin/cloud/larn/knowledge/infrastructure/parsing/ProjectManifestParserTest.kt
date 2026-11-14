package cloud.larn.knowledge.infrastructure.parsing

import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.domain.ProjectContext
import cloud.larn.knowledge.domain.ProjectOverride
import cloud.larn.knowledge.domain.RuleId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class ProjectManifestParserTest {
    private fun success(content: String) =
        ProjectManifestParser.parse(".knowledge.yaml", content).shouldBeInstanceOf<ManifestResult.Success>().context

    private fun errors(content: String) =
        ProjectManifestParser.parse(".knowledge.yaml", content).shouldBeInstanceOf<ManifestResult.Failure>().errors.map { it.message }

    @Test
    fun `parses a full manifest`() {
        success(
            """
            team: payments
            languages: [java, kotlin]
            frameworks: [spring-boot]
            overrides:
              - rule: JAVA-ERR-003
                action: replace
                doc: docs/knowledge/error-handling.md
                reason: Legacy client
              - rule: ORG-REV-001
                action: waive
            """.trimIndent(),
        ) shouldBe ProjectContext(
            team = "payments",
            languages = listOf("java", "kotlin"),
            frameworks = listOf("spring-boot"),
            overrides = listOf(
                ProjectOverride(RuleId("JAVA-ERR-003"), OverrideAction.REPLACE, "docs/knowledge/error-handling.md", "Legacy client"),
                ProjectOverride(RuleId("ORG-REV-001"), OverrideAction.WAIVE),
            ),
        )
    }

    @Test
    fun `every field is optional`() {
        success("languages: [go]") shouldBe ProjectContext(languages = listOf("go"))
    }

    @Test
    fun `reports invalid values`() {
        errors(
            """
            team: Payments
            languages: [java]
            overrides:
              - { rule: java-err-003, action: waive }
              - { rule: JAVA-ERR-004, action: drop }
              - { rule: JAVA-ERR-005, action: extend }
            """.trimIndent(),
        ) shouldContainExactly listOf(
            "Invalid team value 'Payments': expected a lowercase identifier",
            "overrides[0]: invalid rule id 'java-err-003'",
            "overrides[1]: invalid action 'drop': expected one of extend, replace, waive",
            "overrides[2]: 'extend' needs a project doc",
        )
    }

    @Test
    fun `reports unknown keys and duplicate rules`() {
        errors("team: payments\nlanguage: [java]").single() shouldContain "language"
        errors("overrides:\n  - { rule: A-001, action: waive }\n  - { rule: A-001, action: waive }").single() shouldContain
            "More than one override"
    }
}
