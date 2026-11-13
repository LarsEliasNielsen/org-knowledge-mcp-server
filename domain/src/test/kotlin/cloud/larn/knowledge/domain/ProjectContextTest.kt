package cloud.larn.knowledge.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ProjectContextTest {
    private val err003 = RuleId("JAVA-ERR-003")

    @Test
    fun `keeps language order`() {
        ProjectContext(languages = listOf("kotlin", "java")).languages shouldBe listOf("kotlin", "java")
    }

    @Test
    fun `rejects invalid identifiers`() {
        shouldThrow<IllegalArgumentException> { ProjectContext(team = "Payments") }
        shouldThrow<IllegalArgumentException> { ProjectContext(languages = listOf("Java")) }
        shouldThrow<IllegalArgumentException> { ProjectContext(frameworks = listOf("spring boot")) }
    }

    @Test
    fun `rejects duplicate languages`() {
        shouldThrow<IllegalArgumentException> { ProjectContext(languages = listOf("java", "java")) }
    }

    @Test
    fun `rejects two overrides for the same rule`() {
        shouldThrow<IllegalArgumentException> {
            ProjectContext(
                overrides = listOf(
                    ProjectOverride(err003, OverrideAction.WAIVE),
                    ProjectOverride(err003, OverrideAction.REPLACE, doc = "docs/knowledge/a.md"),
                ),
            )
        }
    }

    @Test
    fun `finds the override for a rule`() {
        val waive = ProjectOverride(err003, OverrideAction.WAIVE, reason = "legacy")
        val context = ProjectContext(overrides = listOf(waive))

        context.overrideFor(err003) shouldBe waive
        context.overrideFor(RuleId("JAVA-ERR-004")).shouldBeNull()
    }

    @Test
    fun `extend and replace need a project doc, waive does not`() {
        shouldThrow<IllegalArgumentException> { ProjectOverride(err003, OverrideAction.EXTEND) }
        shouldThrow<IllegalArgumentException> { ProjectOverride(err003, OverrideAction.REPLACE, doc = " ") }
        ProjectOverride(err003, OverrideAction.WAIVE).doc.shouldBeNull()
    }
}
